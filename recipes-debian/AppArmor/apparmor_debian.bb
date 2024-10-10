#base recipe: meta-security/recipes-mac/AppArmor/apparmor_2.13.2.bb
#base branch: warrior
#base commit: 4f7be0d252f68d8e8d442a7ed8c6e8a852872d28

SUMMARY = "AppArmor another MAC control system"
DESCRIPTION = "user-space parser utility for AppArmor \
This provides the system initialization scripts needed to use the \
AppArmor Mandatory Access Control system, including the AppArmor Parser \
which is required to convert AppArmor text profiles into machine-readable \
policies that are loaded into the kernel for use with the AppArmor Linux \
Security Module."
HOMEAPAGE = "http://apparmor.net/"
SECTION = "admin"

LICENSE = "GPLv2 & GPLv2+ & BSD-3-Clause & LGPLv2.1+"
LIC_FILES_CHKSUM = "file://${S}/LICENSE;md5=fd57a4b0bc782d7b80fd431f10bbf9d0"

DEPENDS = "bison-native apr gettext-native coreutils-native"

inherit pkgconfig autotools-brokensep update-rc.d python3native perlnative cpan manpages systemd debian-package
# FIXME:
#   Building apparmor-ptest will fail regardless of TARGET_ARCH because Makefile
#   for apparmor's tests does not support cross-compilation.
#   Some targets are failed to build even though HOST_ARCH and TARGET_ARCH are
#   the same.
#   This recipe will not build and install for ptest if ARCH is aarch64 or arm,
#   but 'inherit ptest' remains to apparmor-ptest package will be created that
#   only contains the 'run-ptest' file.
#   So, remove ptest from inherit because it is not a available quality.
#inherit ptest

SRC_URI += " \
        file://disable_perl_h_check.patch \
        file://crosscompile_perl_bindings.patch \
        file://apparmor.rc \
        file://functions \
        file://apparmor \
        file://apparmor.service \
        file://run-ptest \
        "

PARALLEL_MAKE = ""

require recipes-debian/sources/apparmor.inc

PACKAGECONFIG ??= "python perl"
PACKAGECONFIG[manpages] = "--enable-man-pages, --disable-man-pages"
PACKAGECONFIG[python] = "--with-python, --without-python, python3 swig-native"
PACKAGECONFIG[perl] = "--with-perl, --without-perl, perl perl-native swig-native"
PACKAGECONFIG[apache2] = ",,apache2,"

PAMLIB="${@bb.utils.contains('DISTRO_FEATURES', 'pam', '1', '0', d)}"
HTTPD="${@bb.utils.contains('PACKAGECONFIG', 'apache2', '1', '0', d)}"


python() {
    if 'apache2' in d.getVar('PACKAGECONFIG').split() and \
            'webserver' not in d.getVar('BBFILE_COLLECTIONS').split():
        raise bb.parse.SkipRecipe('Requires meta-webserver to be present.')
}

DISABLE_STATIC = ""

do_configure() {
    cd ${S}/libraries/libapparmor
    aclocal
    autoconf --force
    libtoolize --automake -c --force
    automake -ac
    ./configure ${CONFIGUREOPTS} ${EXTRA_OECONF}
}

do_compile () {
    # Fixes:
    # | sed -ie 's///g' Makefile.perl
    # | sed: -e expression #1, char 0: no previous regular expression
    #| Makefile:478: recipe for target 'Makefile.perl' failed
    sed -i "s@sed -ie 's///g' Makefile.perl@@" ${S}/libraries/libapparmor/swig/perl/Makefile


    oe_runmake -C ${B}/libraries/libapparmor
    oe_runmake -C ${B}/binutils
    oe_runmake -C ${B}/utils
    oe_runmake -C ${B}/parser
    oe_runmake -C ${B}/profiles

    if test -z "${HTTPD}" ; then
    oe_runmake -C ${B}/changehat/mod_apparmor
    fi

    if test -z "${PAMLIB}" ; then
    oe_runmake -C ${B}/changehat/pam_apparmor
    fi
}

do_install () {
    install -d ${D}/${INIT_D_DIR}
    install -d ${D}/lib/apparmor

    oe_runmake -C ${B}/libraries/libapparmor DESTDIR="${D}" install
    oe_runmake -C ${B}/binutils DESTDIR="${D}" install
    oe_runmake -C ${B}/utils DESTDIR="${D}" install
    oe_runmake -C ${B}/parser DESTDIR="${D}" install
    oe_runmake -C ${B}/profiles DESTDIR="${D}" install

    # If perl is disabled this script won't be any good
    if ! ${@bb.utils.contains('PACKAGECONFIG','perl','true','false', d)}; then
    rm -f ${D}${sbindir}/aa-notify
    fi

    if test -z "${HTTPD}" ; then
    oe_runmake -C ${B}/changehat/mod_apparmor DESTDIR="${D}" install
    fi

    if test -z "${PAMLIB}" ; then
    oe_runmake -C ${B}/changehat/pam_apparmor DESTDIR="${D}" install
    fi

    # aa-easyprof is installed by python-tools-setup.py, fix it up
    sed -i -e 's:/usr/bin/env.*:/usr/bin/python3:' ${D}${bindir}/aa-easyprof
    chmod 0755 ${D}${bindir}/aa-easyprof

    install ${WORKDIR}/apparmor ${D}/${INIT_D_DIR}/apparmor
    install ${WORKDIR}/functions ${D}/lib/apparmor
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/apparmor.service ${D}${systemd_system_unitdir}
}

do_install_append() {
    #Install files add by debian package
    #debian/apparmor.install
    install -d ${D}${datadir}/apport/package-hooks
    install ${S}/debian/apport/source_apparmor.py ${D}${datadir}/apport/package-hooks
    install -d ${D}${datadir}/apparmor-features
    install ${S}/debian/features ${D}${datadir}/apparmor-features
    install ${S}/debian/lib/apparmor/profile-load ${D}/lib/apparmor
    install ${S}/parser/apparmor.systemd ${D}/lib/apparmor
    install ${S}/parser/rc.apparmor.functions ${D}/lib/apparmor/
    install ${S}/parser/aa-teardown ${D}${sbindir}
    install -D ${S}/debian/apparmor.lintian-overrides ${D}${datadir}/lintian/overrides/apparmor
    #debian/apparmor-utils.install
    install ${S}/debian/aa-update-browser ${D}${sbindir}
    install -D ${S}/debian/vim-apparmor.yaml ${D}${datadir}/vim/registry/vim-apparmor.yaml
    install -D ${D}${datadir}/apparmor/apparmor.vim ${D}${datadir}/vim/addons/syntax/apparmor.vim
    install -D ${S}/debian/apparmor-utils.lintian-overrides ${D}${datadir}/lintian/overrides/apparmor-utils
    #debian/apparmor-easyprof.install
    install -D ${S}/debian/apparmor-easyprof.lintian-overrides ${D}${datadir}/lintian/overrides/apparmor-easyprof
}

#Backport to exclude arm and aarch64 ptest tasks
#apparmor: ptest fail to build on arm
#commit: 27ddb455543b670097e252ba0d0ad5b7e4101748
#Building ptest on arm fails.
do_compile_ptest_aarch64 () {
  :
}

do_compile_ptest_arm () {
  :
}

do_compile_ptest () {
    oe_runmake -C ${B}/tests/regression/apparmor
    oe_runmake -C ${B}/parser/tst
    oe_runmake -C ${B}/libraries/libapparmor
}

#Backport to exclude arm and aarch64 ptest tasks
#apparmor: ptest fail to build on arm
##commit: 27ddb455543b670097e252ba0d0ad5b7e4101748
#Building ptest on arm fails.
do_install_ptest_aarch64 () {
  :
}

do_install_ptest_arm() {
  :
}

do_install_ptest () {
    t=${D}/${PTEST_PATH}/testsuite
    install -d ${t}
    install -d ${t}/tests/regression/apparmor
    cp -rf ${B}/tests/regression/apparmor ${t}/tests/regression

    install -d ${t}/parser/tst
    cp -rf ${B}/parser/tst ${t}/parser
    cp ${B}/parser/apparmor_parser ${t}/parser
    cp ${B}/parser/frob_slack_rc ${t}/parser

    install -d ${t}/libraries/libapparmor
    cp -rf ${B}/libraries/libapparmor ${t}/libraries

    install -d ${t}/common
    cp -rf ${B}/common ${t}

    install -d ${t}/binutils
    cp -rf ${B}/binutils ${t}
}

python rm_sysvinit_initddir () {
    pass
}

INITSCRIPT_PACKAGES = "${PN}"
INITSCRIPT_NAME = "apparmor"
INITSCRIPT_PARAMS = "start 16 2 3 4 5 . stop 35 0 1 6 ."

SYSTEMD_PACKAGES = "${PN}"
SYSTEMD_SERVICE_${PN} = "apparmor.service"
SYSTEMD_AUTO_ENABLE = "disable"

PACKAGES += "mod-${PN}"

FILES_${PN} += "/lib/apparmor/ ${sysconfdir}/apparmor ${libdir}/python3/dist-packages ${datadir}"
FILES_mod-${PN} = "${libdir}/apache2/modules/*"

RDEPENDS_${PN} += "bash lsb coreutils glibc-utils findutils"
RDEPENDS_${PN} += "${@bb.utils.contains('PACKAGECONFIG','python','python3 python3-modules','', d)}"
RDEPENDS_${PN}_remove += "${@bb.utils.contains('PACKAGECONFIG','perl','','perl', d)}"
RDEPENDS_${PN}-ptest += "perl coreutils dbus-lib bash"

