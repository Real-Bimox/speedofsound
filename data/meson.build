fs = import('fs')
desktop_file = fs.copyfile('ai.nexiant.voicestream.desktop.in', 'ai.nexiant.voicestream.desktop',
  install: true,
  install_dir: get_option('datadir') / 'applications'
)

desktop_utils = find_program('desktop-file-validate', required: false, disabler: true)
test('Validate desktop file', desktop_utils, args: [desktop_file])

appstream_file = fs.copyfile('ai.nexiant.voicestream.metainfo.xml.in', 'ai.nexiant.voicestream.metainfo.xml',
  install: true,
  install_dir: get_option('datadir') / 'metainfo'
)

appstreamcli = find_program('appstreamcli', required: false, disabler: true)
test('Validate appstream file', appstreamcli,
     args: ['validate', '--no-net', '--explain', appstream_file]
)

install_data('ai.nexiant.voicestream.gschema.xml',
  install_dir: get_option('datadir') / 'glib-2.0' / 'schemas'
)

compile_schemas = find_program('glib-compile-schemas', required: false, disabler: true)
test('Validate schema file', compile_schemas,
     args: ['--strict', '--dry-run', meson.current_source_dir()]
)

service_conf = configuration_data()
service_conf.set('bindir', get_option('prefix') / get_option('bindir'))
configure_file(
          input: 'ai.nexiant.voicestream.service.in',
         output: 'ai.nexiant.voicestream.service',
  configuration: service_conf,
    install_dir: get_option('datadir') / 'dbus-1' / 'services'
)

subdir('icons')
