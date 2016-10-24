define([
  'common/config',
  'document/annotation/common/editor/sections/section'
], function(Config, Section) {

  var DELETE_WIDTH = 23,

      ANIM_DURATION = 150;

  var TagSection = function(parent, annotation) {
    var element = (Config.writeAccess) ? jQuery(
          '<div class="section tags">' +
            '<ul></ul>' +
            '<div contenteditable="true" spellcheck="false" class="add-tag" data-placeholder="Add tag..." />' +
          '</div>') : jQuery('<div class="section tags readonly"><ul></ul></div>'),

        taglist = element.find('ul'),

        textarea = element.find('.add-tag'),

        queuedUpdates = [],

        escapeHtml = function(text) {
          return jQuery('<div/>').text(text).html();
        },

        /**
         * Creates a new tag element and attaches the tag to it as data.
         *
         * Takes either a tag object or a string as input.
         */
        createTag = function(charsOrTag) {
          var tag = (charsOrTag.type) ? charsOrTag :
                { type: 'TAG', last_modified_by: Config.me, value: charsOrTag.trim() },

              li = jQuery('<li><span class="label">' + escapeHtml(tag.value) + '</span>' +
                '<span class="delete"><span class="icon">&#xf014;</span></span></li>');

          li.data('tag', tag);
          return li;
        },

        /** Initializes the tag list from the annotation bodies **/
        init = function() {
          var tagCount = 0;
          jQuery.each(annotation.bodies, function(idx, body) {
            if (body.type === 'TAG') {
              taglist.append(createTag(body));
              tagCount++;
            }
          });

          // In read-only mode, hide the list if there are no tags
          if (!Config.writeAccess && tagCount === 0)
            element.hide();
        },

        /** Tests if the given character string exists as a tag already **/
        exists = function(chars) {
          var existing = jQuery.grep(taglist.children(), function(el) {
            var tagChars = jQuery(el).find('.label').text();
            return tagChars === chars;
          });
          return existing.length > 0;
        },

        /** Adds a new tag to the annotation **/
        addTag = function(chars) {
          if (!exists(chars)) {
            var li = createTag(chars),
                tag = li.data('tag');

            taglist.append(li);
            queuedUpdates.push(function() { annotation.bodies.push(tag); });
          }
        },

        /** Deletes a tag from the annotation **/
        deleteTag = function(li) {
          var tag = li.data('tag');
          li.remove();
          queuedUpdates.push(function() {
            var idx = annotation.bodies.indexOf(tag);
            if (idx > -1)
              annotation.bodies.splice(idx, 1);
          });
        },

        /** Shows the delete button on the given tag element **/
        showDeleteButton = function(li) {
          var delIcon = li.find('.delete');
          li.animate({ 'padding-right' : DELETE_WIDTH }, ANIM_DURATION);
          delIcon.animate({ 'width': DELETE_WIDTH }, ANIM_DURATION);
        },

        /** Hides all currently visible delete buttons **/
        hideAllDeleteButtons = function() {
          jQuery.each(taglist.find('li'), function(idx, el) {
            var li = jQuery(el),
                delIcon = li.find('.delete');
                isClicked = delIcon.width() > 0;

            if (isClicked) {
              li.animate({ 'padding-right' : 0 }, ANIM_DURATION);
              delIcon.animate({ 'width': 0 }, ANIM_DURATION);
            }
          });
        },

        /** Click toggles the delete button or deletes, depending on state & click target **/
        onTagClicked = function(e) {
          var isDelete = (e.target).closest('.delete'),
              li = jQuery(e.target).closest('li'),
              chars = li.find('.label').text();

          if (isDelete) {
            deleteTag(li);
          } else {
            showDeleteButton(li);
            hideAllDeleteButtons();
          }
        },

        /** Text entry field: new tags are created on ENTER **/
        onKeyDown = function(e) {
          if (e.keyCode === 13) {
            var tags = textarea.text().split(',');
            jQuery.each(tags, function(idx, chars) {
              addTag(chars.trim());
            });
            textarea.empty();
            textarea.blur();
            return false;
          }
        },

        /** @override **/
        hasChanged = function() {
          return queuedUpdates.length > 0;
        },

        /** @override **/
        commit = function() {
          jQuery.each(queuedUpdates, function(idx, fn) { fn(); });
        },

        /** @override **/
        destroy = function() {
          element.remove();
        };

    init();

    if (Config.writeAccess) {
      taglist.on('click', 'li', onTagClicked);
      textarea.keydown(onKeyDown);
    }

    parent.append(element);

    this.hasChanged = hasChanged;
    this.commit = commit;
    this.destroy = destroy;
    this.body = {}; // N/A

    Section.apply(this);
  };
  TagSection.prototype = Object.create(Section.prototype);

  return TagSection;

});
