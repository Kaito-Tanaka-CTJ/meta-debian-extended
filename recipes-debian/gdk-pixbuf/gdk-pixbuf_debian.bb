# base recipe: meta/recipes-gnome/gdk-pixbuf/gdk-pixbuf_2.38.0.bb
# base branch: warrior
# base commit: 37a5993c7ee93ab06204abc9f066cb1df502f553

SUMMARY = "Image loading library for GTK+"
HOMEPAGE = "http://www.gtk.org/"
BUGTRACKER = "https://bugzilla.gnome.org/"

inherit debian-package
require recipes-debian/sources/${BPN}.inc

LICENSE = "LGPLv2.1+"
LIC_FILES_CHKSUM = "file://COPYING;md5=4fbd65380cdd255951079008b364516c \
                    file://gdk-pixbuf/gdk-pixbuf.h;endline=26;md5=72b39da7cbdde2e665329fef618e1d6b \
                    "

SECTION = "libs"

DEPENDS = "glib-2.0 gdk-pixbuf-native shared-mime-info"

FILESPATH_append = ":${COREBASE}/meta/recipes-gnome/gdk-pixbuf/gdk-pixbuf"
SRC_URI += "\
	file://run-ptest \
	file://fatal-loader.patch \
	file://0001-Work-around-thumbnailer-cross-compile-failure.patch \
	file://0004-Do-not-run-tests-when-building.patch \
"
SRC_URI_append_class-target = " \
	file://0003-target-only-Work-around-thumbnailer-cross-compile-fa.patch \
	file://0006-Build-thumbnailer-and-tests-also-in-cross-builds.patch \
	file://missing-test-data.patch \
	file://1000-tests-meson-build-Work-around-test-build-errors-in-c.patch \
"
SRC_URI_append_class-nativesdk = "file://0003-target-only-Work-around-thumbnailer-cross-compile-fa.patch"

inherit meson pkgconfig gettext pixbufcache ptest-gnome upstream-version-is-even gobject-introspection gtk-doc lib_package

GTKDOC_ENABLE_FLAG = "-Ddocs=true"
GTKDOC_DISABLE_FLAG = "-Ddocs=false"

GIR_MESON_OPTION = 'gir'

EXTRA_OEMESON_append_class-target = " ${@bb.utils.contains('GTKDOC_ENABLED', 'True', '${GTKDOC_ENABLE_FLAG}', \
                                                                                     '${GTKDOC_DISABLE_FLAG}', d)} "

EXTRA_OEMESON_append = " ${@bb.utils.contains('PTEST_ENABLED', '1', '-Dinstalled_tests=true', '-Dinstalled_tests=false', d)}"

LIBV = "2.10.0"

GDK_PIXBUF_LOADERS ?= "png jpeg"

PACKAGECONFIG ??= "${GDK_PIXBUF_LOADERS}"
PACKAGECONFIG_linuxstdbase = "${@bb.utils.filter('DISTRO_FEATURES', 'x11', d)} ${GDK_PIXBUF_LOADERS}"
PACKAGECONFIG_class-native = "${GDK_PIXBUF_LOADERS}"

PACKAGECONFIG[png] = "-Dpng=true,-Dpng=false,libpng"
PACKAGECONFIG[jpeg] = "-Djpeg=true,-Djpeg=false,jpeg"
PACKAGECONFIG[tiff] = "-Dtiff=true,-Dtiff=false,tiff"
PACKAGECONFIG[jpeg2000] = "-Djasper=true,-Djasper=false,jasper"

PACKAGECONFIG[x11] = "-Dx11=true,-Dx11=false,virtual/libx11"

PACKAGES =+ "${PN}-xlib"

# For GIO image type sniffing
RDEPENDS_${PN} = "shared-mime-info"

FILES_${PN}-xlib = "${libdir}/*pixbuf_xlib*${SOLIBS}"
ALLOW_EMPTY_${PN}-xlib = "1"

FILES_${PN} += "${libdir}/gdk-pixbuf-2.0/gdk-pixbuf-query-loaders"

FILES_${PN}-bin += "${datadir}/thumbnailers/gdk-pixbuf-thumbnailer.thumbnailer"

FILES_${PN}-dev += " \
	${bindir}/gdk-pixbuf-csource \
	${bindir}/gdk-pixbuf-pixdata \
	${bindir}/gdk-pixbuf-print-mime-types \
	${includedir}/* \
	${libdir}/gdk-pixbuf-2.0/${LIBV}/loaders/*.la \
"

PACKAGES_DYNAMIC += "^gdk-pixbuf-loader-.*"
PACKAGES_DYNAMIC_class-native = ""

python populate_packages_prepend () {
    postinst_pixbufloader = d.getVar("postinst_pixbufloader")

    loaders_root = d.expand('${libdir}/gdk-pixbuf-2.0/${LIBV}/loaders')

    packages = ' '.join(do_split_packages(d, loaders_root, r'^libpixbufloader-(.*)\.so$', 'gdk-pixbuf-loader-%s', 'GDK pixbuf loader for %s'))
    d.setVar('PIXBUF_PACKAGES', packages)

    # The test suite exercises all the loaders, so ensure they are all
    # dependencies of the ptest package.
    d.appendVar("RDEPENDS_%s-ptest" % d.getVar('PN'), " " + packages)
}

do_install_append() {
	# Copy gdk-pixbuf-query-loaders into libdir so it is always available
	# in multilib builds.
	cp ${D}/${bindir}/gdk-pixbuf-query-loaders ${D}/${libdir}/gdk-pixbuf-2.0/
}

do_install_append_class-native() {
	find ${D}${libdir} -name "libpixbufloader-*.la" -exec rm \{\} \;

	create_wrapper ${D}/${bindir}/gdk-pixbuf-csource \
		XDG_DATA_DIRS=${STAGING_DATADIR} \
		GDK_PIXBUF_MODULE_FILE=${STAGING_LIBDIR_NATIVE}/gdk-pixbuf-2.0/${LIBV}/loaders.cache

	create_wrapper ${D}/${bindir}/gdk-pixbuf-pixdata \
		XDG_DATA_DIRS=${STAGING_DATADIR} \
		GDK_PIXBUF_MODULE_FILE=${STAGING_LIBDIR_NATIVE}/gdk-pixbuf-2.0/${LIBV}/loaders.cache

	create_wrapper ${D}/${bindir}/gdk-pixbuf-print-mime-types \
		XDG_DATA_DIRS=${STAGING_DATADIR} \
		GDK_PIXBUF_MODULE_FILE=${STAGING_LIBDIR_NATIVE}/gdk-pixbuf-2.0/${LIBV}/loaders.cache

	create_wrapper ${D}/${libdir}/gdk-pixbuf-2.0/gdk-pixbuf-query-loaders \
		XDG_DATA_DIRS=${STAGING_DATADIR} \
		GDK_PIXBUF_MODULE_FILE=${STAGING_LIBDIR_NATIVE}/gdk-pixbuf-2.0/${LIBV}/loaders.cache \
		GDK_PIXBUF_MODULEDIR=${STAGING_LIBDIR_NATIVE}/gdk-pixbuf-2.0/${LIBV}/loaders

	create_wrapper ${D}/${bindir}/gdk-pixbuf-query-loaders \
		XDG_DATA_DIRS=${STAGING_DATADIR} \
		GDK_PIXBUF_MODULE_FILE=${STAGING_LIBDIR_NATIVE}/gdk-pixbuf-2.0/${LIBV}/loaders.cache \
		GDK_PIXBUF_MODULEDIR=${STAGING_LIBDIR_NATIVE}/gdk-pixbuf-2.0/${LIBV}/loaders
}
BBCLASSEXTEND = "native nativesdk"
