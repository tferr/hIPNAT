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

import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

import ij.Menus;

public class Utils {

	public boolean validSkelDependencies() {
		return classExists(
				Arrays.asList("sc.fiji.analyzeSkeleton.AnalyzeSkeleton_", "sc.fiji.skeletonize3D.Skeletonize3D_"));
	}

	public boolean classExists(final List<String> classStringNames) {
		if (classStringNames != null) {
			for (final String cls : classStringNames) {
				try {
					Class.forName(cls);
				} catch (final ClassNotFoundException e) {
					IPNAT.handleException(e);
					return false;
				}
			}
		}
		return true;
	}

	public boolean classExists(final String classStringName) {
		return classExists(Collections.singletonList(classStringName));
	}

	public boolean commandExists(final List<String> commandLabels) {
		@SuppressWarnings("rawtypes")
		final Hashtable commands = Menus.getCommands();
		for (final String commandLabel : commandLabels) {
			if (commands.get(commandLabel) == null)
				return false;
		}
		return true;
	}

	public boolean commandExists(final String commandLabel) {
		return commandExists(Collections.singletonList(commandLabel));
	}

}
