require.config({
  baseUrl: "/assets/javascripts/",
  fileExclusionRegExp: /^lib$/
});

require([
  'common/ui/alert',
  'common/ui/touch',
  'common/config'
], function(Alert, Touch, Config) {

  jQuery(document).ready(function() {
        /** Document elements **/
    var documents = jQuery('.document'),

        /** Tool buttons **/
        btnDeleteSelected = jQuery('button.delete'),
        btnCreateFolder = jQuery('button.add-folder'),

        /** Search **/
        searchbox = jQuery('input.search'),

        /** TODO these will be normal links later **/
        btnAccountSettings = jQuery('.account-settings'),
        btnGridView = jQuery('.display-mode'),

        /** Resolves the click target to the parent document element **/
        getClickedDocument = function(e) {
          var docEl = jQuery(e.target).closest('.document');
          if (docEl.length > 0)
            return docEl;
          else
            return false;
        },

        /** Returns the IDs of the currently selected documents **/
        getSelectedDocumentIDs = function() {
          var selected = jQuery.grep(documents, function(docEl) {
            return jQuery(docEl).hasClass('selected');
          });

          return jQuery.map(selected, function(el) {
            return el.dataset.id;
          });
        },

        /** Deselects list and disables the trashcan icon **/
        deselectAll = function() {
          documents.removeClass('selected');
          btnDeleteSelected.addClass('disabled');
        },

        /** User clicked the trashcan icon **/
        onClickDelete = function() {
          var title = '<span class="icon">&#xf071;</span> Delete Document',
              message = 'You cannot undo this operation. Are you sure you want to do this?',
              alert = new Alert(Alert.WARNING, title, message);

          alert.on('ok', deleteDocuments);

          return false;
        },

        /** Temporary: user clicked an icon representing an unimplemented feature **/
        onClickUnimplemented = function() {
          alert('This feature is not implemented yet (bear with us).');
          return false;
        },

        /**
         * Global click handler on the document, so we can
         * de-select if user clicks anywhere on the page
         */
        onClick = function(e) {
          var doc = getClickedDocument(e);
          if (doc) {
            if (!e.ctrlKey)
              deselectAll();

            btnDeleteSelected.removeClass('disabled');
            doc.addClass('selected');
          } else {
            // Click was outside the document list
            deselectAll();
          }
        },

        /** Deletes documents sequentially **/
        deleteDocuments = function() {
          var ids = getSelectedDocumentIDs(),
              head, tail;

          if (ids.length > 0) {
            head = ids[0];
            tail = ids.slice(1);

            jsRoutes.controllers.document.settings.SettingsController.deleteDocument(head).ajax()
              .fail(function(error) {
                console.log(error);
              })
              .done(function(result) {
                deleteDocuments(tail);
              });
          } else {
            window.location.reload(true);
          }
        },

        openDocument = function(e) {
          var id = getClickedDocument(e).data('id'),
              url = jsRoutes.controllers.document.annotation.AnnotationController
                      .showAnnotationView(id, 1).absoluteURL();

          window.location.href = url;
        };

    btnDeleteSelected.click(onClickDelete);

    // TODO temporary: register dummy handlers on icons for unimplemented features
    btnCreateFolder.click(onClickUnimplemented);
    btnGridView.click(onClickUnimplemented);
    searchbox.keyup(function(e) {
      if (e.which === 13)
        onClickUnimplemented();
    });

    // Register global click handler, so we can handle de-selects
    jQuery(document).click(onClick);

    // Double click on documents opens them
    jQuery('.document-panel').on('dblclick', '.document', openDocument);

    // If mobile, explicitely enable tap events alongside normal click events
    if (Config.IS_TOUCH) {
      Touch.enableTouchEvents();
      jQuery(document).on('tap', onClick);
      jQuery('.document-panel').on('doubletap', '.document', openDocument);
    }
  });

});
