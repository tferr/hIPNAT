# @ImagePlus(label="Particles image") impPart
# @ImagePlus(label="Skeletonizable mask", description="Must be a binary image (black background)") impSkel
# @String(label="AutoThreshold for particle detection", choices={"Default", "Huang", "Intermodes", "IsoData", "IJ_IsoData", "Li", "MaxEntropy", "Mean", "MinError", "Minimum", "Moments", "Otsu", "Percentile", "RenyiEntropy", "Shanbhag", "Triangle", "Yen"}) thres_method
# @Double(label="Max. \"snap to\" distance", description="In calibrated units", min=1, max=1000, style="scroll bar", value=3) cutoff_dist
# @Boolean(label="Display measurements", value=false) display_measurements
# @UIService uiService
# @ImageJ ij

"""
    Classify_Particles_Using_Skeleton.py
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    Tags particles according to skeleton features: Detects maxima on a masked
    image and clusters detected particles according to their positioning to
    features of the skeletonized mask. A maxima is considered to be associated
    to a skeleton feature (node, tip, etc.) if the distance between its centroid
    and the feature is less than or equal to a cuttoff ("snap to") distance.

    :version: 201706
    :copyright: 2016,2017 TF
    :url: https://github.com/tferr/hIPNAT
    :license: GPL3, see LICENSE for more details
"""

from ij import IJ
from ij.gui import Overlay, PointRoi
from ij.measure import ResultsTable

from ipnat.processing import Binary
from sc.fiji.skeletonize3D import Skeletonize3D_
from sc.fiji.analyzeSkeleton import AnalyzeSkeleton_
from net.imagej.table import DefaultGenericTable, GenericColumn
from java.awt import Color
import math, sys


def addToTable(table, column_header, value):
    """ Adds the specified value to the specifed column of an IJ table """
    col_idx = table.getColumnIndex(column_header)
    if col_idx == -1:
        column = GenericColumn(column_header)
        column.add(value)
        table.add(column)
    else:
        column = table.get(col_idx)
        column.add(value)
        table.remove(col_idx)
        table.add(col_idx, column)


def cleanse_overlay(overlay):
    """ Removes all point ROIs from the specified overlay """
    if not overlay:
        return Overlay()
    for i in reversed(range(overlay.size())):
        roi = overlay.get(i)
        if isinstance(roi, PointRoi):
            overlay.remove(i)
    return overlay


def distance(x1, y1, x2, y2):
     """ Calculates the distance between 2D points """
     return math.sqrt((x2 - x1)**2 + (y2 - y1)**2)


def error(msg):
    """ Displays an error message """
    uiService.showDialog(msg, "Error")


def get_centroids(imp, tolerance):
    """ Returns maxima using IJ1 """
    from ij.plugin.filter import MaximumFinder
    mf = MaximumFinder()
    maxima = mf.getMaxima(imp.getProcessor(), tolerance, True)
    return maxima.xpoints, maxima.ypoints, maxima.npoints


def get_threshold(imp, method):
    #from ij.process import AutoThresholder
    #from ij.process import ImageStatistics
    #thresholder = AutoThresholder()
    #method = AutoThresholder.Method.Moments
    #stats = ip.getStatistics()
    #result = thresholder.getThreshold(method, stats.histogram)
    arg = "%s dark" % method
    IJ.setAutoThreshold(imp, arg)
    value = imp.getProcessor().getMinThreshold()
    IJ.resetThreshold(imp)
    return value

def pixel_size(imp):
    """ Returns the smallest pixel length of the specified image """
    cal = imp.getCalibration()
    return min(cal.pixelWidth, cal.pixelHeight)


def ratio(n, total):
    """ Returns a readable frequency for the specified ratio """
    a = "0 (0.0%)" if total is 0 else "%d (%d%%)" % (n, round(float(n)/total*100, 3))
    #print(a)
    return n


def skeleton_properties(imp):
    """ Retrieves lists of endpoints, junction points, junction
        voxels and total length from a skeletonized image
    """
    skel_analyzer = AnalyzeSkeleton_()
    skel_analyzer.setup("", imp)
    skel_result = skel_analyzer.run()

    avg_lengths = skel_result.getAverageBranchLength()
    n_branches = skel_result.getBranches()
    lengths = [n*avg for n,avg in zip(n_branches, avg_lengths)]
    total_length = sum(lengths)

    return (skel_result.getListOfEndPoints(), skel_result.getJunctions(),
            skel_result.getListOfJunctionVoxels(), total_length)


def skeletonize(imp):
    """ Skeletonizes the specified image in situ """
    thin = Skeletonize3D_()
    thin.setup("", imp)
    thin.run(None)
    Binary.removeIsolatedPixels(imp)


def run():

    mask_ip = impSkel.getProcessor()
    part_ip = impPart.getProcessor()

    if not mask_ip.isBinary():
        error(impSkel.getTitle() + " is not a binary mask.")
        return

    # Mask grayscale image and skeletonize mask
    mask_pixels = mask_ip.getPixels()
    part_pixels = part_ip.getPixels()
    for i in xrange(len(part_pixels)):  
        if mask_pixels[i] == 0:  
            part_pixels[i] = 0
    part_ip.setPixels(part_pixels)
    skeletonize(impSkel)

    # Get skeleton features
    end_points, junctions, junction_voxels, total_len = skeleton_properties(impSkel)
    if not end_points and not junction_voxels:
        error(impSkel.getTitle() + " does not seem a valid skeleton.")
        return

    # Retrieve centroids from IJ1
    threshold_lower = get_threshold(impPart, thres_method)
    cx, cy, n_particles = get_centroids(impPart, threshold_lower)
    if None in (cx, cy):
        error("Verify parameters: No particles detected.")
        return

    # Loop through each centroids and categorize its position
    # according to its distance to skeleton features
    n_bp = n_tip = n_none = n_both = 0

    overlay = cleanse_overlay(impPart.getOverlay())
    for i in range(n_particles):

        j_dist = ep_dist = sys.maxint

        # Retrieve the distance between this particle and the closest junction voxel
        for jvoxel in junction_voxels:
            dist = distance(cx[i], cy[i], jvoxel.x, jvoxel.y)
            if (dist <= cutoff_dist and dist < j_dist):
                j_dist = dist

        # Retrieve the distance between this particle and the closest end-point
        for end_point in end_points:
            dist = distance(cx[i], cy[i], end_point.x, end_point.y)
            if (dist <= cutoff_dist and dist < ep_dist):
                ep_dist = dist

        roi_id = str(i).zfill(len(str(n_particles)))
        roi_name = "Unknown:" + roi_id
        roi_color = Color.BLUE

        # Is particle associated with neither junctions nor end-points?
        if j_dist > cutoff_dist and ep_dist > cutoff_dist:
            roi_name = "NoFeature:" + roi_id
            n_none += 1
        # Is particle associated with both?
        elif abs(j_dist - ep_dist) <= pixel_size(impPart) / 2:
            roi_name = "JunctionAndTip:" + roi_id
            roi_color = Color.RED
            n_both += 1
        # Is particle associated with an end-point?
        elif ep_dist < j_dist:
            roi_name = "Tip:" + roi_id
            roi_color = Color.GREEN
            n_tip += 1
        # Is particle associated with a junction?
        elif ep_dist > j_dist:
            roi_name = "Junction:" + roi_id
            roi_color = Color.MAGENTA
            n_bp += 1

        roi = PointRoi(cx[i], cy[i])
        roi.setName(roi_name)
        roi.setStrokeColor(roi_color)
        overlay.add(roi)

    # Display result
    impSkel.setOverlay(overlay)
    impPart.setOverlay(overlay)

    # Output some measurements
    if display_measurements:

#        t = DefaultGenericTable()
#        t.appendRow(impPart.getTitle())
#        addToTable(t, "Skel image", impSkel.getTitle())
#        addToTable(t, "Junction particles", ratio(n_bp, n_particles))
#        addToTable(t, "Tip particles", ratio(n_tip, n_particles))
#        addToTable(t, "J+T particles", ratio(n_both, n_particles))
#        addToTable(t, "Unc. particles", ratio(n_none, n_particles))
#        addToTable(t, "Junctions w/ particles", ratio(n_bp+n_both, sum(junctions)))
#        addToTable(t, "Tips w/ particles", ratio(n_tip+n_both, len(end_points)))
#        addToTable(t, "Total skel. lenght", total_len)
#        addToTable(t, "Density (N. unc. particles/ Total skel. lenght)", n_none/total_len)
#        addToTable(t, "'Snap-to' dist.", str(cutoff_dist) + impPart.getCalibration().getUnits())
#        addToTable(t, "Threshold range", "%d-%d" % (threshold_lower, threshold_upper))
#        addToTable(t, "Size range", "%d-%d" % (size_min, size_max))
#        ij.ui().show("Skeleton Classified Particles", t)

        table = ResultsTable.getResultsTable()
        row = table.getCounter()
        table.showRowNumbers(False)
        table.setNaNEmptyCells(False)    
        table.setValue("Part. image", row, "%s (%s)" % (impPart.getTitle(), impPart.getCalibration().getUnits()))
        table.setValue("Skel. image", row, "%s (%s)" % (impSkel.getTitle(), impSkel.getCalibration().getUnits()))
        table.setValue("Junction particles", row, ratio(n_bp, n_particles))
        table.setValue("Tip particles", row, ratio(n_tip, n_particles))
        table.setValue("J+T particles", row, ratio(n_both, n_particles))
        table.setValue("Unc. particles", row, ratio(n_none, n_particles))
        table.setValue("Junctions w/ particles", row, ratio(n_bp+n_both, sum(junctions)))
        table.setValue("Tips w/ particles", row, ratio(n_tip+n_both, len(end_points)))
        table.setValue("Total skel. lenght", row, total_len)
        table.setValue("Total end points", row, len(end_points))
        table.setValue("Total junctions", row, sum(junctions))
        table.setValue("Density (N. unc. particles/ Total skel. lenght)", row, n_none/total_len)
        table.setValue("'Snap-to' dist.", row, str(cutoff_dist) + impPart.getCalibration().getUnits())
        table.setValue("Threshold", row, "%d (%s)" % (threshold_lower, thres_method))
        table.show("Results")


run()
