/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package server;

import static org.jboss.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static org.jboss.netty.handler.codec.http.HttpHeaders.setContentLength;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static org.jboss.netty.handler.codec.http.HttpMethod.GET;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import model.Doc;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.ObjectMapper;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelFutureProgressListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.DefaultFileRegion;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.FileRegion;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.handler.stream.ChunkedFile;
import org.jboss.netty.util.CharsetUtil;

public class HttpServerHandler extends SimpleChannelUpstreamHandler {


	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
			throws Exception {
		HttpRequest request = (HttpRequest) e.getMessage();
		String uri = request.getUri();
		// On recherche dans les routes si c'est l'appel a un Controlleur
		if (uri.startsWith("/doc")) {
			System.out.println("Request Ok ");

			System.out.println(request.getMethod());

			byte[] arrayJson = request.getContent().array();

			JsonFactory jsonFactory = new JsonFactory();
			JsonParser jsonParser = jsonFactory.createJsonParser(arrayJson);

			ObjectMapper mapper = new ObjectMapper();

			Doc d = mapper.readValue(jsonParser, Doc.class);

			System.out.println("params : " + d.title);
			
		}

		// Sinon on sert un fichier
		else {

			if (request.getMethod() != GET) {
				sendError(ctx, METHOD_NOT_ALLOWED);
				return;
			}

			final String path = sanitizeUri("public" + request.getUri());
			if (path == null) {
				sendError(ctx, FORBIDDEN);
				return;
			}
			System.out.println("path : " + path);
			File file = new File(path);
			if (file.isHidden() || !file.exists()) {
				sendError(ctx, NOT_FOUND);
				return;
			}
			if (!file.isFile()) {
				sendError(ctx, FORBIDDEN);
				return;
			}

			RandomAccessFile raf;
			try {
				raf = new RandomAccessFile(file, "r");
			} catch (FileNotFoundException fnfe) {
				sendError(ctx, NOT_FOUND);
				return;
			}
			long fileLength = raf.length();

			HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
			setContentLength(response, fileLength);

			Channel ch = e.getChannel();

			// Write the initial line and the header.
			ch.write(response);

			// Write the content.
			ChannelFuture writeFuture;
			if (ch.getPipeline().get(SslHandler.class) != null) {
				// Cannot use zero-copy with HTTPS.
				writeFuture = ch
						.write(new ChunkedFile(raf, 0, fileLength, 8192));
			} else {
				// No encryption - use zero-copy.
				final FileRegion region = new DefaultFileRegion(
						raf.getChannel(), 0, fileLength);
				writeFuture = ch.write(region);
				writeFuture.addListener(new ChannelFutureProgressListener() {
					public void operationComplete(ChannelFuture future) {
						region.releaseExternalResources();
					}

					public void operationProgressed(ChannelFuture future,
							long amount, long current, long total) {
						System.out.printf("%s: %d / %d (+%d)%n", path, current,
								total, amount);
					}
				});
			}

			// Decide whether to close the connection or not.
			if (!isKeepAlive(request)) {
				// Close the connection when the whole content is written out.
				writeFuture.addListener(ChannelFutureListener.CLOSE);
			}
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
			throws Exception {
		Channel ch = e.getChannel();
		Throwable cause = e.getCause();
		if (cause instanceof TooLongFrameException) {
			sendError(ctx, BAD_REQUEST);
			return;
		}

		cause.printStackTrace();
		if (ch.isConnected()) {
			sendError(ctx, INTERNAL_SERVER_ERROR);
		}
	}

	private String sanitizeUri(String uri) {
		// Decode the path.
		try {
			uri = URLDecoder.decode(uri, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			try {
				uri = URLDecoder.decode(uri, "ISO-8859-1");
			} catch (UnsupportedEncodingException e1) {
				throw new Error();
			}
		}

		// Convert file separators.
		uri = uri.replace('/', File.separatorChar);

		// Simplistic dumb security check.
		// You will have to do something serious in the production environment.
		if (uri.contains(File.separator + ".")
				|| uri.contains("." + File.separator) || uri.startsWith(".")
				|| uri.endsWith(".")) {
			return null;
		}

		// Convert to absolute path.
		return System.getProperty("user.dir") + File.separator + uri;
	}

	private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
		HttpResponse response = new DefaultHttpResponse(HTTP_1_1, status);
		response.setHeader(CONTENT_TYPE, "text/plain; charset=UTF-8");
		response.setContent(ChannelBuffers.copiedBuffer(
				"Failure: " + status.toString() + "\r\n", CharsetUtil.UTF_8));

		// Close the connection as soon as the error message is sent.
		ctx.getChannel().write(response)
				.addListener(ChannelFutureListener.CLOSE);
	}

}

