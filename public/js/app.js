(function($) {

	window.Doc = Backbone.Model.extend({

		defaults : {
			id : "???",
			title : "Doc Title"
		},

		validate : function(attributes) {
			if (attributes.title == '') {
				return "Le titre du document ne peut pas être vide !!!";
			}
		},

		initialize : function Doc() {
			console.log('Doc Constructor');

			this.url = "http://localhost:8080/doc"

			this.bind("error", function(model, error) {
				console.log(error);
			});

		},

		getId : function() {
			return this.get('id');
		},
		setId : function(value) {
			this.set({
				id : value
			});
		},

		getTitle : function() {
			return this.get('title');
		},
		setTitle : function(value) {
			this.set({
				title : value
			});
		},

		getText : function() {
			return this.get('text');
		},
		setText : function(value) {
			this.set({
				text : value
			});
		},

		getKeywords : function() {
			return this.get('keywords');
		},
		setKeywords : function(value) {
			this.set({
				keywords : value
			});
		}

	});

	window.Docs = Backbone.Collection.extend({
		model : Doc,

		// localStorage : new Store("docs"),
		// local: false, // always fetched and saved locally
		// remote: true, // never cached, dualStorage is bypassed entirely
		url : "http://localhost:8080/docs",

		initialize : function() {
			console.log('Docs collection Constructor');
		}
	});

})(jQuery);
