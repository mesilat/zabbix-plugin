import $ from 'jquery';
import UserGroupSelect2 from 'user-group-select2';

let isAdmin = false;

function error(msg) {
  if ('responseText' in msg) {
    alert(msg.responseText);
  } else {
    alert(msg);
  }
}
function d(data) {
  console.log('zabbix-plugin d', data);
  return d;
}

async function getConnections() {
  return $.ajax({
    url: `${AJS.contextPath()}/rest/zabbix-plugin/1.0/connection`,
    type: 'GET',
    dataType: 'json',
  });
}
async function deleteConnection(id) {
  return $.ajax({
    url: `${AJS.contextPath()}/rest/zabbix-plugin/1.0/connection?id=${id}`,
    type: 'DELETE',
  });
}
async function postConnection(data) {
  return $.ajax({
    url: `${AJS.contextPath()}/rest/zabbix-plugin/1.0/connection`,
    type: 'POST',
    contentType: 'application/json',
    data: JSON.stringify(data),
    processData: false,
    dataType: 'json',
  });
}
async function setDefault(id) {
  return $.ajax({
    url: `${AJS.contextPath()}/rest/zabbix-plugin/1.0/connection/set-default?id=${id}`,
    type: 'POST',
    contentType: 'application/json',
    data: JSON.stringify(id),
    processData: false,
    dataType: 'text',
  });
}

async function getFormats() {
  return $.ajax({
    url: `${AJS.contextPath()}/rest/zabbix-plugin/1.0/format`,
    type: 'GET',
    dataType: 'json',
  });
}
async function deleteFormat(id) {
  return $.ajax({
    url: `${AJS.contextPath()}/rest/zabbix-plugin/1.0/format?id=${id}`,
    type: 'DELETE',
  });
}
async function postFormat(data) {
  return $.ajax({
    url: `${AJS.contextPath()}/rest/zabbix-plugin/1.0/format`,
    type: 'POST',
    contentType: 'application/json',
    data: JSON.stringify(data),
    processData: false,
    dataType: 'json',
  });
}

async function initConnectionDescriptorTable($div) {
  // try {
  const data = await getConnections();
  const $table = $(Mesilat.Zabbix.Templates.connections({
    recs: data.results,
    admin: isAdmin,
    user: AJS.params.remoteUser,
  }));

  $table.appendTo($div.empty());
  const $rows = $table.find('tbody > tr');
  for (let i = 0; i < data.results.length; i++) {
    const $tr = $($rows[i]);
    $tr.prop('data', data.results[i]);
    initEditConnectionDescriptorButton($tr.find('button.com-mesilat-zabbix-edit'));
    initDeleteConnectionDescriptorButton($tr.find('button.com-mesilat-zabbix-delete'));
    initSetDefaultConnectionDescriptorButton($tr.find('button.com-mesilat-zabbix-set-default'));
  }
  // } catch(err){
  //  error(err);
  // }
}
function initEditConnectionDescriptorButton($btn) {
  const $tr = $btn.closest('tr');
  $btn.on('click', () => {
    $tr.hide();
    createEditConnectionDescriptorForm($tr);
  });
}
async function showConfirmDeleteConnectionDescriptorDialog() {
  const deferred = $.Deferred();

  const $dlg = $(Mesilat.Zabbix.Templates.deleteConnection({}));
  $dlg.on('click', 'button.delete', () => {
    deferred.resolve(true);
    AJS.dialog2($dlg).hide();
  });
  $dlg.on('click', 'button.cancel', () => {
    deferred.resolve(false);
    AJS.dialog2($dlg).hide();
  });
  AJS.dialog2($dlg).show();
  return deferred.promise();
}
function initDeleteConnectionDescriptorButton($btn) {
  const $tr = $btn.closest('tr');
  $btn.on('click', async () => {
    const rec = $tr.prop('data');
    const confirm = await showConfirmDeleteConnectionDescriptorDialog();
    if (confirm) {
      try {
        await deleteConnection(rec.id);
        $tr.remove();
      } catch (err) {
        error(err);
      }
    }
  });
}
function initSetDefaultConnectionDescriptorButton($btn) {
  const $tr = $btn.closest('tr');
  $btn.on('click', async () => {
    const rec = $tr.prop('data');
    try {
      await setDefault(rec.id);
      initConnectionDescriptorTable($tr.closest('#com-mesilat-zabbix-servers'));
    } catch (err) {
      error(err);
    }
  });
}
function initCreateNewConnectionDescriptor() {
  $('#com-mesilat-zabbix-server-new').on('click', (e) => {
    e.preventDefault();

    const $wrapper = $(e.target).parent();
    const html = $wrapper[0].innerHTML;
    const $form = $(Mesilat.Zabbix.Templates.connectionDescriptor({}));
    $wrapper.empty().append($form);
    UserGroupSelect2.bind($form); // Using standard user-group-selector

    $form.on('click', '#com-mesilat-zabbix-save', async (e) => {
      e.preventDefault();
      const connection = {
        url: $form.find('input[name = "url"]').val(),
        username: $form.find('input[name = "username"]').val(),
        password: $form.find('input[name = "password"]').val(),
        grantees: $form.find('input[name = "grantees"]').val(),
      };
      try {
        $form.spin();
        AJS.dim();
        const data = await postConnection(connection);

        $form
          .find('div.aui-message')
          .html($('<p>').text(AJS.I18n.getText('com.mesilat.zabbix-plugin.success.save-connection')))
          .css('display', 'block')
          .removeClass()
          .addClass('aui-message aui-message-success fadeout');
        setTimeout(() => {
          $form.find('.fadeout').each(function () {
            $(this).removeClass('fadeout');
            $(this).hide(500);
          });
          setTimeout(() => {
            if (data.results) {
              const $tr = $(Mesilat.Zabbix.Templates.connection({
                rec: data.results,
                admin: isAdmin,
                user: AJS.params.remoteUser,
              }));
              $('#com-mesilat-zabbix-servers').find('tbody').append($tr);
              $tr.prop('data', data.results);
              initEditConnectionDescriptorButton($tr.find('button.com-mesilat-zabbix-edit'));
              initDeleteConnectionDescriptorButton($tr.find('button.com-mesilat-zabbix-delete'));
              initSetDefaultConnectionDescriptorButton($tr.find('button.com-mesilat-zabbix-set-default'));
              $wrapper.html(html);
              initCreateNewConnectionDescriptor();
            }
          }, 500);
        }, 2000);
      } catch (err) {
        console.error('zabbix-plugin', err);
        $form
          .find('div.aui-message')
          .html($('<p>').text(err.responseText))
          .css('display', 'block')
          .removeClass()
          .addClass('aui-message aui-message-error fadeout');
        setTimeout(() => {
          $form.find('.fadeout').each(function () {
            $(this).removeClass('fadeout');
            $(this).hide(500);
          });
        }, 2000);
      } finally {
        AJS.undim();
        $form.spinStop();
      }
    });

    $form.on('click', '#com-mesilat-zabbix-cancel', (e) => {
      e.preventDefault();
      $wrapper.html(html);
      initCreateNewConnectionDescriptor();
    });
  });
}
function createEditConnectionDescriptorForm($tr) {
  const rec = $tr.prop('data');
  console.log('zabbix-plugin EditConnectionDescriptor', rec);
  const $tr2 = $(`<tr><td colspan="${($tr.find('td').length)}"><div class="com-mesilat-zabbix-intable-form"></div></td></tr>`).insertBefore($tr);
  const $div = $tr2.find('div');
  const $form = $(Mesilat.Zabbix.Templates.connectionDescriptor(rec));
  $div.append($form);

  const $grantees = $form.find('#com-mesilat-zabbix-grantees');
  UserGroupSelect2.bind($form);
  if (rec.grantees && rec.grantees !== '') {
    let grantees = rec.grantees.split(',');
    if (!_.isArray(grantees)) {
      grantees = [grantees];
    }
    const granteeData = [];
    grantees.forEach((grantee) => {
      granteeData.push({
        id: grantee,
        text: rec.fullNames[grantee],
        imgSrc: rec.images[grantee],
      });
    });
    $grantees.auiSelect2('data', granteeData);
  }

  $form.find('#com-mesilat-zabbix-save').on('click', async () => {
    const connection = {
      id: $form.find('input[name = "id"]').val(),
      url: $form.find('input[name = "url"]').val(),
      username: $form.find('input[name = "username"]').val(),
      password: $form.find('input[name = "password"]').val(),
      grantees: $form.find('input[name = "grantees"]').val(),
    };
    try {
      $form.spin();
      AJS.dim();
      const data = await postConnection(connection);

      $form
        .find('div.aui-message')
        .html($('<p>').text(AJS.I18n.getText('com.mesilat.zabbix-plugin.success.save-connection')))
        .css('display', 'block')
        .removeClass()
        .addClass('aui-message aui-message-success fadeout');
      setTimeout(() => {
        $form.find('.fadeout').each(function () {
          $(this).removeClass('fadeout');
          $(this).hide(500);
        });
        setTimeout(() => {
          if (data.results) {
            $tr.remove();
            $tr = $(Mesilat.Zabbix.Templates.connection({
              rec: data.results,
              admin: isAdmin,
              user: AJS.params.remoteUser,
            }));
            $tr2.replaceWith($tr);
            $tr.prop('data', data.results);
            initEditConnectionDescriptorButton($tr.find('button.com-mesilat-zabbix-edit'));
            initDeleteConnectionDescriptorButton($tr.find('button.com-mesilat-zabbix-delete'));
            initSetDefaultConnectionDescriptorButton($tr.find('button.com-mesilat-zabbix-set-default'));
          }
        }, 500);
      }, 2000);
    } catch (err) {
      $form
        .find('div.aui-message')
        .html($('<p>').text(err.responseText))
        .css('display', 'block')
        .removeClass()
        .addClass('aui-message aui-message-error fadeout');
      setTimeout(() => {
        $form.find('.fadeout').each(function () {
          $(this).removeClass('fadeout');
          $(this).hide(500);
        });
      }, 2000);
    } finally {
      AJS.undim();
      $form.spinStop();
    }
  });

  $form.find('#com-mesilat-zabbix-cancel').on('click', (e) => {
    e.preventDefault();
    $div.closest('tbody').find('tr').show();
    $div.closest('tr').remove();
  });
}

function showFormats() {
  $('#com-mesilat-zabbix-formats').each(async function () {
    const $div = $(this);
    try {
      const data = await getFormats();
      const $template = $(Mesilat.Zabbix.Templates.formats({
        recs: data.results,
        admin: isAdmin,
        user: AJS.params.remoteUser,
      }));
      $template.appendTo($div);
      for (let i = 0; i < data.results.length; i++) {
        const $tr = $($template.find('tr')[i + 1]); // Skip header row
        $tr.prop('data', data.results[i]);
        initEditFormatButton($tr.find('button.com-mesilat-zabbix-edit'));
        initDeleteFormatButton($tr.find('button.com-mesilat-zabbix-delete'));
      }
    } catch (err) {
      error(err);
    }
  });
}
function initEditFormatButton($btn) {
  const $tr = $btn.closest('tr');
  $btn.on('click', (e) => {
    e.preventDefault();
    $tr.hide();
    createEditFormatForm($tr);
  });
}
async function showConfirmDeleteFormatDialog() {
  const deferred = $.Deferred();

  const $dlg = $(Mesilat.Zabbix.Templates.deleteFormat({}));
  $dlg.on('click', 'button.delete', () => {
    deferred.resolve(true);
    AJS.dialog2($dlg).hide();
  });
  $dlg.on('click', 'button.cancel', () => {
    deferred.resolve(false);
    AJS.dialog2($dlg).hide();
  });
  AJS.dialog2($dlg).show();
  return deferred.promise();
}
function initDeleteFormatButton($btn) {
  const $tr = $btn.closest('tr');
  $btn.on('click', async () => {
    const rec = $tr.prop('data');
    const confirm = await showConfirmDeleteFormatDialog();
    if (confirm) {
      try {
        await deleteFormat(rec.id);
        $tr.remove();
      } catch (err) {
        error(err);
      }
    }
  });
}
function initCreateNewFormatLink() {
  $('#com-mesilat-zabbix-format-new').on('click', (e) => {
    const $wrapper = $(e.target).parent();
    const html = $wrapper[0].innerHTML;
    const $form = $(Mesilat.Zabbix.Templates.namedFormat({}));
    $wrapper.empty().append($form);

    $form.find('#com-mesilat-zabbix-formats-save').on('click', async (e) => {
      e.preventDefault();

      const namedFormat = {};
      if ($form.find('input[name = "id"]').val() !== '') {
        namedFormat.id = $form.find('input[name = "id"]').val();
      }
      if ($form.find('input[name = "name"]').val() !== '') {
        namedFormat.name = $form.find('input[name = "name"]').val();
      }
      if ($form.find('input[name = "format"]').val() !== '') {
        namedFormat.format = $form.find('input[name = "format"]').val();
      }
      if ($form.find('input[name = "public"]').is(':checked')) {
        namedFormat.public = true;
      } else {
        namedFormat.public = false;
      }

      try {
        $form.spin();
        AJS.dim();
        const data = await postFormat(namedFormat);

        $form
          .find('div.aui-message')
          .html($('<p>').text(AJS.I18n.getText('com.mesilat.zabbix-plugin.success.save-format')))
          .css('display', 'block')
          .removeClass()
          .addClass('aui-message aui-message-success fadeout');
        setTimeout(() => {
          $form.find('.fadeout').each(function () {
            $(this).removeClass('fadeout');
            $(this).hide(500);
          });
          setTimeout(() => {
            if (data.results) {
              const $tr = $(Mesilat.Zabbix.Templates.format({
                rec: data.results,
                admin: isAdmin,
                user: AJS.params.remoteUser,
              }));
              $('#com-mesilat-zabbix-formats').find('tbody').append($tr);
              $tr.prop('data', data.results);
              initEditFormatButton($tr.find('button.com-mesilat-zabbix-edit'));
              initDeleteFormatButton($tr.find('button.com-mesilat-zabbix-delete'));
              $wrapper.html(html);
              initCreateNewFormatLink();
            }
          }, 500);
        }, 2000);
      } catch (err) {
        $form
          .find('div.aui-message')
          .html($('<p>').text(err.responseText))
          .css('display', 'block')
          .removeClass()
          .addClass('aui-message aui-message-error fadeout');
        setTimeout(() => {
          $form.find('.fadeout').each(function () {
            $(this).removeClass('fadeout');
            $(this).hide(500);
          });
        }, 2000);
      } finally {
        AJS.undim();
        $form.spinStop();
      }
    });

    $form.find('#com-mesilat-zabbix-formats-cancel').on('click', (e) => {
      $wrapper.html(html);
      initCreateNewFormatLink();
    });
  });
}
function createEditFormatForm($tr) {
  const rec = $tr.prop('data');

  const $tr2 = $(`<tr><td colspan="${($tr.find('td').length)}"><div class="com-mesilat-zabbix-intable-form"></div></td></tr>`).insertBefore($tr);
  const $div = $tr2.find('div');
  const $form = $(Mesilat.Zabbix.Templates.namedFormat(rec));
  $div.append($form);

  $form.find('#com-mesilat-zabbix-formats-save').on('click', async (e) => {
    e.preventDefault();
    const namedFormat = {};
    if ($form.find('input[name = "id"]').val() !== '') {
      namedFormat.id = $form.find('input[name = "id"]').val();
    }
    if ($form.find('input[name = "name"]').val() !== '') {
      namedFormat.name = $form.find('input[name = "name"]').val();
    }
    if ($form.find('input[name = "format"]').val() !== '') {
      namedFormat.format = $form.find('input[name = "format"]').val();
    }
    if ($form.find('input[name = "public"]').is(':checked')) {
      namedFormat.public = true;
    } else {
      namedFormat.public = false;
    }
    try {
      $form.spin();
      AJS.dim();
      const data = await postFormat(namedFormat);

      $form
        .find('div.aui-message')
        .html($('<p>').text(AJS.I18n.getText('com.mesilat.zabbix-plugin.success.save-format')))
        .css('display', 'block')
        .removeClass()
        .addClass('aui-message aui-message-success fadeout');
      setTimeout(() => {
        $form.find('.fadeout').each(function () {
          $(this).removeClass('fadeout');
          $(this).hide(500);
        });
        setTimeout(() => {
          if (data.results) {
            $tr.remove();
            $tr = $(Mesilat.Zabbix.Templates.format({
              rec: data.results,
              admin: isAdmin,
              user: AJS.params.remoteUser,
            }));
            $tr2.replaceWith($tr);
            $tr.prop('data', data.results);
            initEditFormatButton($tr.find('button.com-mesilat-zabbix-edit'));
            initDeleteFormatButton($tr.find('button.com-mesilat-zabbix-delete'));
          }
        }, 500);
      }, 2000);
    } catch (err) {
      $form
        .find('div.aui-message')
        .html($('<p>').text(err.responseText))
        .css('display', 'block')
        .removeClass()
        .addClass('aui-message aui-message-error fadeout');
      setTimeout(() => {
        $form.find('.fadeout').each(function () {
          $(this).removeClass('fadeout');
          $(this).hide(500);
        });
      }, 2000);
    } finally {
      AJS.undim();
      $form.spinStop();
    }
  });

  $form.find('#com-mesilat-zabbix-formats-cancel').on('click', (e) => {
    $div.closest('tbody').find('tr').show();
    $div.closest('tr').remove();
  });
}

function init(admin) {
  isAdmin = admin;
  console.debug(`zabbix-plugin settings (admin: ${admin})`);
  $('#com-mesilat-zabbix-servers').each(function () {
    initConnectionDescriptorTable($(this));
  });

  initCreateNewConnectionDescriptor();
  showFormats();
  initCreateNewFormatLink();
}

export default () => $(() => {
  init($('div.com-mesilat-zabbix-globalsettings').length > 0);
});
