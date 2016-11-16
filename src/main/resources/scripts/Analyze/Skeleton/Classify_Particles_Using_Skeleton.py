# @ImagePlus(label="Particles image") impPart
# @Double(label="Lower threshold", min=1, max=80000, style="scroll bar", value=900) threshold_lower
# @Double(label="Upper threshold", min=1, max=80000, style="scroll bar", value=65535) threshold_upper
# @Double(label="Smallest particle size", description="In calibrated units", min=1, max=1000, style="scroll bar", value=1.5) size_min
# @Double(label="Largest particle size", description="In calibrated units", min=1, max=1000, style="scroll bar", value=100) size_max
# @String(value=" ", visibility="MESSAGE") spacer
# @ImagePlus(label="Skeletonizable image", description="Must be 8-bit") impSkel
# @Double(label="Max. \"snap to\" distance", description="In calibrated units", min=1, max=1000, style="scroll bar", value=5) cutoff_dist
# @Boolean(label="Display measurements", value=false) display_measurements
# @UIService uiService
# @ImageJ ij

"""
    Classify_Particles_Using_Skeleton.py
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    Tags particles according to skeleton features: Runs IJ's ParticleAnalyzer on
    one image and clusters detected particles according to their positioning to
    features of a tagged skeleton in another image. A particle is considered to
    be associated to a skeleton feature if the distance between its centroid and
    the feature is less than or equal to a cuttoff ("snap to") distance.

    :version: 20161116
    :copyright: 2016 TF
    :url: https://github.com/tferr/hIPNAT
    :license: GPL3, see LICENSE for more details
"""

from ij import IJ
from ij.measure import Measurements as M, ResultsTable
from ij.plugin.frame import RoiManager
from ij.plugin.filter import ParticleAnalyzer as PA

from ipnat.processing import Binary
from sc.fiji.skeletonize3D import Skeletonize3D_
from sc.fiji.analyzeSkeleton import AnalyzeSkeleton_, SkeletonResult

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

def distance(x1, y1, x2, y2):
     """ Calculates the distance between 2D points """
     return math.sqrt((x2 - x1)**2 + (y2 - y1)**2)

def error(msg):
    """ Displays an error message """
    uiService.showDialog(msg, "Error")

def getRoiManager():
    """ Returns an empty IJ1 ROI Manager """
    from org.scijava.ui.DialogPrompt import MessageType, OptionType, Result
    rm = RoiManager.getInstance()
    if rm is None:
        rm = RoiManager()
    elif rm.getCount() > 0:
        mt = MessageType.WARNING_MESSAGE
        ot = OptionType.YES_NO_OPTION
        result = uiService.showDialog("Clear All ROIs on the list?", "ROI Manager", mt, ot)
        if result is Result.YES_OPTION:
            rm.reset()
        else:
            rm = None
    return rm

def getCentroids(imp, lower_t, upper_t, min_size, max_size):
    """ Returns centroids from the ParticleAnalyzer"""
    rt = ResultsTable()
    impPart.deleteRoi()
    IJ.setThreshold(impPart, lower_t, upper_t, "No Update")
    pa = PA(PA.ADD_TO_MANAGER, M.CENTROID, rt, min_size, max_size)
    pa.analyze(impPart)
    IJ.resetThreshold(impPart)
    return rt.getColumn(ResultsTable.X_CENTROID), rt.getColumn(ResultsTable.Y_CENTROID)

def log(*arg):
    """ Convenience log function """
    ls.info("%s" % ''.join(map(str, arg)))

def ratio(n, total):
    """ Returns a readable frequency for the specified ratio """
    return "0 (0.0%)" if total is 0 else (str(n) + " (" + str(round(float(n)/total*100, 3)) + "%)")

def skeleton_properties(imp):
    """ list of endpoints, junction points, and junction voxels from a skeleton """
    skel_analyzer = AnalyzeSkeleton_()
    skel_analyzer.setup("", imp)
    skel_result = skel_analyzer.run()
    return skel_result.getListOfEndPoints(), skel_result.getJunctions(), skel_result.getListOfJunctionVoxels()

def skeletonize(imp):
    """ Skeletonizes the specified image """
    thin = Skeletonize3D_()
    thin.setup("", imp)
    thin.run(None)
    Binary.removeIsolatedPixels(imp)

def pixel_size(imp):
    """ Returns the smallest pixel length of the specified image """
    cal = imp.getCalibration()
    return min(cal.pixelWidth, cal.pixelHeight)


def run():

    # Get skeleton features
    if impSkel.getBitDepth() != 8:
        error(impSkel.getTitle() + " cannot be processed: It is not 8-bit")
        return

    skeletonize(impSkel)
    end_points, junctions, junction_voxels = skeleton_properties(impSkel)
    if not end_points and not junction_voxels:
        error(impSkel.getTitle() + " does not seem a valid skeleton.")
        return

    # For convenience we'll store particles in the ROI Manager
    rm = getRoiManager()
    if rm is None:
        return

    # Retrieve centroids from IJ1 ParticleAnalyzer
    cx, cy = getCentroids(impPart, threshold_lower, threshold_upper, size_min, size_max)
    if not cx or not cy:
        error("Verify parameters: No particles detected.")
        return

    # Loop through particles' centroids and categorize each particle according
    # to its distance to skeleton features. The procedure is simple enough
    # that we can use traced ROIs directly
    n_particles = len(cx)
    min_distance = pixel_size(impPart)
    n_bp = n_tip = n_none = n_both = 0
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

        roi_id = str(i).zfill(n_particles)
        roi_name = "Unknown:" + roi_id
        roi_color = Color.BLUE

        # Is particle associated with neither junctions nor end-points?
        if j_dist > cutoff_dist and ep_dist > cutoff_dist:
            roi_name = "NoFeature:" + roi_id
            n_none += 1
        # Is particle associated with both?
        elif abs(j_dist - ep_dist) <= min_distance:
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

        roi = rm.getRoi(i)
        roi.setName(roi_name)
        roi.setStrokeColor(roi_color)

    # Display result
    rm.runCommand(impPart, "Show All")
    rm.runCommand(impSkel, "Show All")

    # Output some measurements
    if display_measurements:
        rm.runCommand(impPart, "Deselect")
        rm.runCommand(impPart, "Measure")

        t = DefaultGenericTable()
        t.appendRow(impPart.getTitle())
        addToTable(t, "Junction particles", ratio(n_bp, n_particles))
        addToTable(t, "Tip particles", ratio(n_tip, n_particles))
        addToTable(t, "J+T particles", ratio(n_both, n_particles))
        addToTable(t, "Unc. particles", ratio(n_none, n_particles))
        addToTable(t, "Junctions w/ particles", ratio(n_bp+n_both, sum(junctions)))
        addToTable(t, "Tips w/ particles", ratio(n_tip+n_both, len(end_points)))
        addToTable(t, "Max 'snap-to' dist.", str(cutoff_dist) + impPart.getCalibration().getUnits())
        addToTable(t, "Threshold range", "%d-%d" % (threshold_lower, threshold_upper))
        addToTable(t, "Size range", "%d-%d" % (size_min, size_max))
        ij.ui().show("Skeleton Classified Particles", t)


run()
