require.config({
  baseUrl: "/assets/javascripts/",
  fileExclusionRegExp: /^lib$/
});

require(['common/config'], function(Config) {

  jQuery(document).ready(function() {
    var previewTemplate = '<div class="dz-preview dz-file-preview">' +
          '  <div class="dz-details">' +
          '    <div class="dz-size" data-dz-size=""></div>' +
          '    <div class="dz-filename"><span data-dz-name=""></span></div>' +
          '  </div>' +
          '  <div class="dz-progress"><span class="dz-upload" data-dz-uploadprogress=""></span></div>' +
          '  <a class="dz-remove" title="Click to remove the file" data-dz-remove></a>' +
          '  <div class="dz-error-message"><span data-dz-errormessage=""></span></div>' +
          '</div>',

        btnNext = jQuery('input.next'),

        nerPanel = jQuery('.ner'),

        nerCheckbox = jQuery('#apply-ner'),

        // State maintained independently, since we need to uncheck when hiding the panel!
        nerCheckboxState = true,

        /** Returns true if there are any text uploads in the list **/
        doUploadsIncludeText = function() {
          var uploadsInList = jQuery('.dz-preview'),
              textUploads = jQuery.grep(uploadsInList, function(el) {
                var contentType = jQuery(el).data('type');
                return contentType && contentType.indexOf('TEXT_') === 0;
              });

          return textUploads.length > 0;
        },

        /** Returns true if there are un-uploaded files in the list **/
        areUploadsPending = function() {
          // Note: we're setting 'data-type' after upload - querying this seems
          // a lot quicker than using dropzone's queue API
          var uploadsInList = jQuery('.dz-preview'),
              unfinishedUploads = jQuery.grep(uploadsInList, function(el) {
                return !jQuery(el).data('type');
              });

          return unfinishedUploads.length > 0;
        },

        /** Refreshes the view, updating NER option visibility and NEXT button state **/
        refresh = function() {
          var uploadContainer = jQuery('#uploaded'),
              uploadsInList = jQuery('.dz-preview'),
              successfulUploadsInList = uploadsInList.not('.upload-failed'),
              isNerPanelVisible = nerPanel.is(':visible'),
              uploadsIncludeTexts = doUploadsIncludeText();

          // Hide the uploads container if empty (otherwise it would add CSS margin/padding) **/
          if (uploadsInList.length === 0)
            uploadContainer.hide();
          else
            uploadContainer.show();

          // 'Next' button is enabled only if >0 successful uploads & no pending uploads
          if (successfulUploadsInList.length > 0 && !areUploadsPending())
            jQuery('input.next').prop('disabled', false);
          else
            jQuery('input.next').prop('disabled', true);

          // NER panel is visible only if >0 text uploads in list

          if (!isNerPanelVisible && uploadsIncludeTexts) {
            // Hidden but needs to be visible: restore checkbox state and show
            nerCheckbox.prop('checked', nerCheckboxState);
            nerPanel.show();
          } else if (isNerPanelVisible && !uploadsIncludeTexts) {
            // Visible but needs to be hidden: store checkbox state, hide, uncheck
            nerCheckboxState = nerCheckbox.prop('checked');
            nerPanel.hide();
            nerCheckbox.prop('checked', false);
          }
        },

        /** Handles clicks on the trashcan icon next to each upload **/
        onDelete = function(e) {
          var uploadDiv = jQuery(e.target).closest('.dz-preview'),
              filename = (e.name) ? e.name : uploadDiv.find('.dz-filename').text();

          jsRoutes.controllers.my.upload.UploadController.deleteFilepart(Config.owner, filename).ajax({
            success: function(result) {
              uploadDiv.remove();
              refresh();
            }
          });

          refresh();
        },

        onUploadSuccess = function(e, response) {
          // Set content type returned by server to DOM element data-type attribute
          e.previewElement.dataset.type = response.content_type;
          refresh();
        },

        onUploadError = function(e, response) {
          jQuery(e.previewElement).addClass('upload-failed');
          refresh();
        },

        registerIIIFSource = function(url) {
          jsRoutes.controllers.my.upload.UploadController.storeFilepart(Config.owner).ajax({
            data: { iiif_source: url },

            success: function(result) {
              // TODO just a hack to demo the principle!
              var preview = jQuery(previewTemplate);
              preview.find('.dz-filename span').html(url);
              preview.find('.dz-upload').css('width', '100%');
              jQuery('#uploaded').append(preview);
              
              refresh();
              jQuery('input.next').prop('disabled', false);
            }
          });
        };

      new Dropzone('#dropzone', {
        clickable: '#choose-file',
        createImageThumbnails: false,
        dictRemoveFile: '',
        maxFilesize:200,
        previewsContainer: document.getElementById('uploaded-now'),
        previewTemplate: previewTemplate,

        init: function() {
          this.on('addedfile', refresh);
          this.on('removedfile', onDelete);
          this.on('success', onUploadSuccess);
          this.on('error', onUploadError);
        },

        drop: function(e) {
          var link = e.dataTransfer.getData('URL'),

              // Can hopefully be replaced at some point...
              isIIIFUrl = function(url) {
                if (url.indexOf('http://') === 0 || url.indexOf('http://') === 0)
                  return url.indexOf('/info.json') === url.length - 10;
                else
                  return false;
              };

          if (link && isIIIFUrl(link))
            registerIIIFSource(link);
        }
      });

    jQuery('#uploaded').on('click', '.dz-remove', onDelete);
    refresh();
  });

});
