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
	private static String VERSION = null;
	private static String BUILD = null;

	public static String getFullVersion() {
		return ABBREV_NAME + " v" + version(true);
	}

	public static String getVersion() {
		return ABBREV_NAME + " v" + version(false);
	}

	public static void handleException(Exception e) {
		IJ.setExceptionHandler(new ipnat.ExceptionHandler());
		IJ.handleException(e);
		IJ.setExceptionHandler(null); // Revert to the default behavior
	}

	/**
	 * Retrieves hIPNAT's version
	 *
	 * @param implementationDate
	 *            If {@code true}, a date stamp from the package implementation
	 *            date is appended to the string
	 * @return the version or a non-empty place holder string if version could
	 *         not be retrieved.
	 *
	 */
	private static String version(boolean implementationDate) {
		// See http://stackoverflow.com/questions/1272648/
		// http://blog.soebes.de/blog/2014/01/02/version-information-into-your-appas-with-maven/

		if (VERSION == null) {
			final Package pkg = IPNAT.class.getPackage();
			if (pkg != null)
				VERSION = pkg.getImplementationVersion();
			if (VERSION == null)
				VERSION = "X Dev";
		}
		if (implementationDate) {
			if (BUILD == null) {
				final Class<IPNAT> clazz = IPNAT.class;
				final String className = clazz.getSimpleName() + ".class";
				final String classPath = clazz.getResource(className).toString();
				final String manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1)
						+ "/META-INF/MANIFEST.MF";
				try {
					final Manifest manifest = new Manifest(new URL(manifestPath).openStream());
					final Attributes attr = manifest.getMainAttributes();
					BUILD = attr.getValue("Implementation-Date");
					BUILD = BUILD.substring(0, BUILD.lastIndexOf("T"));
				} catch (final Exception ignored) {
					BUILD = null;
				}
			}
			return (BUILD == null) ? VERSION : VERSION + ", " + BUILD;
		}
		return VERSION;
	}
}
