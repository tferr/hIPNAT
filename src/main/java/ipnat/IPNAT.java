/*
 * #%L
 * hIPNAT plugins for Fiji distribution of ImageJ
 * %%
 * Copyright (C) 2016 Tiago Ferreira
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package ipnat;


import java.net.URL;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import ij.IJ;

public class IPNAT {

	public static final String EXTENDED_NAME = "Image Processing for NeuroAnatomy and Tree-like structures";
	public static final String ABBREV_NAME = "hIPNAT";
	public static final String DOC_URL = "http://imagej.net/Neuroanatomy";
	public static final String SRC_URL = "https://github.com/tferr/hIPNAT";

	/** The hIPNAT version **/
	public static String VERSION = version();

	/** A reference to the build date */
	public static String BUILD_DATE = buildDate();

	/** A reference to the build year */
	public static String BUILD_YEAR = buildYear();

	public static void handleException(final Exception e) {
		IJ.setExceptionHandler(new ipnat.ExceptionHandler());
		IJ.handleException(e);
		IJ.setExceptionHandler(null); // Revert to the default behavior
	}

	public static String getVersion() {
		return ABBREV_NAME + " v" + VERSION;
	}

	/**
	 * Retrieves hIPNAT's version
	 *
	 * @return the version or a non-empty place holder string if version could
	 *         not be retrieved.
	 *
	 */
	private static String version() {
		// http://blog.soebes.de/blog/2014/01/02/version-information-into-your-appas-with-maven/
		if (VERSION == null) {
			final Package pkg = IPNAT.class.getPackage();
			if (pkg != null)
				VERSION = pkg.getImplementationVersion();
			if (VERSION == null)
				VERSION = "X Dev";
		}
		return VERSION;
	}

	/**
	 * Retrieves hIPNAT's implementation date
	 *
	 * @return the implementation date or an empty strong if date could not be
	 *         retrieved.
	 */
	private static String buildDate() {
		// http://stackoverflow.com/questions/1272648/
		if (BUILD_DATE == null) {
			final Class<IPNAT> clazz = IPNAT.class;
			final String className = clazz.getSimpleName() + ".class";
			final String classPath = clazz.getResource(className).toString();
			final String manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1)
					+ "/META-INF/MANIFEST.MF";
			try {
				final Manifest manifest = new Manifest(new URL(manifestPath).openStream());
				final Attributes attr = manifest.getMainAttributes();
				BUILD_DATE = attr.getValue("Implementation-Date");
				BUILD_DATE = BUILD_DATE.substring(0, BUILD_DATE.lastIndexOf("T"));
			} catch (final Exception ignored) {
				BUILD_DATE = "";
			}
		}
		return BUILD_DATE;
	}

	/**
	 * Retrieves hIPNAT's implementation year.
	 *
	 * @return the implementation year or an empty string if date could not be
	 *         retrieved.
	 */
	private static String buildYear() {
		return (BUILD_DATE == null || BUILD_DATE.length() < 4) ? "" : BUILD_DATE.substring(0, 4);
	}
}
