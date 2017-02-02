/*
 * #%L
 * hIPNAT plugins for Fiji distribution of ImageJ
 * %%
 * Copyright (C) 2017 Tiago Ferreira
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
package ipnat.processing;


import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;

/**
 * Static methods for operating on 2D and 3D binary images.
 * 
 * @author Tiago Ferreira
 *
 */
public class Binary {

	public static void removeIsolatedPixels(final ImagePlus imp) {
		final ImageStack stack = imp.getStack();
		for (int i = 1; i <= stack.getSize(); i++)
			((ByteProcessor) stack.getProcessor(i)).erode(8, 0);
	}

}
