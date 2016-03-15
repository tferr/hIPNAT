package ipnat;

/*
 * hIPNAT: (highly effective) Image Processing for NeuroAnatomy and Tree-like Structures
 * https://github.com/tferr/hIPNAT
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation
 * (http://www.gnu.org/licenses/gpl.txt).
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 */

public class IPNAT {

	public static final String VERSION = "0.0.1";
	public static final String BUILD = "01";
	public static final String EXTENDED_NAME = "Image Processing for NeuroAnatomy and Tree-like Strucutres";
	public static final String ABBREV_NAME = "hIPNAT";
	public static final String MENU_NAME = "Neurons & Dendriforms";
	public static final String DOC_URL = "http://imagej.net/hIPNAT";
	public static final String SRC_URL = "https://github.com/tferr/hIPNAT";
	public static final String API_URL = "http://tferr.github.io/hIPNAT/apidocs/";

	public static String getReadableVersion() {
		String s = ABBREV_NAME + " v" + VERSION;
		if (!BUILD.isEmpty())
			s += "-" + BUILD;
		return s;
	}
}
