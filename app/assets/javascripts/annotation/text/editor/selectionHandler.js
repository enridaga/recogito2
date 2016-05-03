define(['../../../common/hasEvents', '../../../common/config'], function(HasEvents, Config) {

  var SelectionHandler = function(rootNode, highlighter) {

    var self = this,

        currentSelection = false,

        trimRange = function(range) {
          var quote = range.toString(),
              leadingSpaces = 0,
              trailingSpaces = 0;

          // Strip & count leading whitespace, adjust range
          while (quote.substring(0, 1) === ' ') {
            leadingSpaces += 1;
            quote = quote.substring(1);
          }

          if (leadingSpaces > 0)
            range.setStart(range.startContainer, range.startOffset + leadingSpaces);

          // Strip & count trailing whitespace, adjust range
          while (quote.substring(quote.length - 1) === ' ') {
            trailingSpaces += 1;
            quote = quote.substring(0, quote.length - 1);
          }

          if (trailingSpaces > 0)
            range.setEnd(range.endContainer, range.endOffset - trailingSpaces);

          return range;
        },

        rangeToAnnotationStub = function(selectedRange) {
          var rangeBefore = rangy.createRange();
          // A helper range from the start of the contentNode to the start of the selection
          rangeBefore.setStart(rootNode, 0);
          rangeBefore.setEnd(selectedRange.startContainer, selectedRange.startOffset);

          return {
            annotates: {
              document: Config.documentId,
              filepart: Config.partId
            },
            anchor: 'char-offset:' + rangeBefore.toString().length,
            bodies: [
              { type: 'QUOTE', value: selectedRange.toString() }
            ]
          };
        },

        /** Helper that clears the visible selection by 'unwrapping' the created span elements **/
        unwrapSelectionSpans = function() {
          jQuery.each(jQuery('.selection'), function(idx, el) {
            jQuery(el).contents().unwrap();
          });
          rootNode.normalize();
        },

        /** cf. http://stackoverflow.com/questions/3169786/clear-text-selection-with-javascript **/
        clearSelection = function() {
          currentSelection = false;
          unwrapSelectionSpans();

          if (window.getSelection) {
            if (window.getSelection().empty)
              window.getSelection().empty();
            else if (window.getSelection().removeAllRanges)
              window.getSelection().removeAllRanges();
          } else if (document.selection) {
            document.selection.empty();
          }
        },

        getSelection = function() {
          return currentSelection;
        },

        onSelect = function(e) {
          var selection = rangy.getSelection(),
              selectedRange, annotationStub, bounds;

          if (!selection.isCollapsed &&
               selection.rangeCount == 1 &&
               selection.getRangeAt(0).toString().trim().length > 0) {

             selectedRange = trimRange(selection.getRangeAt(0));
             annotation = rangeToAnnotationStub(selectedRange);
             bounds = selectedRange.nativeRange.getBoundingClientRect();

             clearSelection();
             highlighter.wrapRange(selectedRange, 'selection');

             currentSelection = { annotation: annotation, bounds: bounds };
             self.fireEvent('select', currentSelection);
          }

          return false;
        };

    jQuery(rootNode).mouseup(onSelect);

    this.getSelection = getSelection;
    this.clearSelection = clearSelection;

    HasEvents.apply(this);
  };
  SelectionHandler.prototype = Object.create(HasEvents.prototype);

  return SelectionHandler;

});
