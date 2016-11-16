# @ImagePlus(label="Particles image") impPart
# @Double(label="Lower threshold", min=1, max=80000, style="scroll bar", value=900) threshold_lower
# @Double(label="Upper threshold", min=1, max=80000, style="scroll bar", value=65535) threshold_upper
# @Double(label="Smallest particle size", description="In calibrated units", min=1, max=1000, style="scroll bar", value=1.5) size_min
# @Double(label="Largest particle size", description="In calibrated units", min=1, max=1000, style="scroll bar", value=100) size_max
# @String(value=" ", visibility="MESSAGE") spacer
# @ImagePlus(label="Skeletonizable image", description="Must be 8-bit") impSkel
# @Double(label="Max. \"snap to\" distance", description="In calibrated units", min=1, max=1000, style="scroll bar", value=5) cutoff_dist
# @Boolean(label="Measure particles", description="According to Analyze>Set Measurements...", value=false) measure_rois
# @LogService ls

"""
    Classify_Particles_Using_Skeleton.py
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    Tags particles according to skeleton features: Runs IJ's ParticleAnalyzer on
    one image and clusters detected particles according to their positioning to
    features of a tagged skeleton in another image. A particle is considered to
    be associated to a skeleton feature if the distance between its centroid and
    the feature is less than or equal to a cuttoff ("snap to") distance.

    :version: 20161020
    :copyright: 2016 TF
    :url: https://github.com/tferr/hIPNAT
    :license: GPL3, see LICENSE for more details
"""


from ij import IJ, Macro
from ij.measure import Measurements as M, ResultsTable
from ij.plugin.frame import RoiManager
from ij.plugin.filter import Analyzer, ParticleAnalyzer
from sc.fiji.analyzeSkeleton import AnalyzeSkeleton_, SkeletonResult
import math, sys

def distance(x1, y1, x2, y2):
     return math.sqrt((x2 - x1)**2 + (y2 - y1)**2)

# Retrieve list of skeleton landmarks (here, end/junction points)
skel_analyzer = AnalyzeSkeleton_()
skel_analyzer.setup("", impSkel);
skel_result = skel_analyzer.run();
end_points = skel_result.getListOfEndPoints()
n_junctions = sum(skel_result.getJunctions())
junction_voxels= skel_result.getListOfJunctionVoxels()
if not end_points and not junction_voxels:
    raise RuntimeError(impSkel.getTitle() + " does not seem a valid skeleton.")

# Ensure no data from previous runs exists
rt = ResultsTable()
rt.reset()
rm = RoiManager.getInstance()
if rm is None:
    rm = RoiManager()
rm.reset()

# Retrieve centroids from ParticleAnalyzer. Ensure everything went as planned
imp.deleteRoi()
IJ.setThreshold(imp, threshold_lower, threshold_upper, "No Update")
pa = ParticleAnalyzer(ParticleAnalyzer.ADD_TO_MANAGER, M.CENTROID, rt, size_min, size_max)
pa.analyze(imp)
try:
    cx = rt.getColumn(ResultsTable.X_CENTROID) #X_CENTER_OF_MASS
    cy = rt.getColumn(ResultsTable.Y_CENTROID) #Y_CENTER_OF_MASS
    n_particles = len(cx)
except:
    raise RuntimeError("Verify parameters: No particles detected.")

# Loop through particles' centroids and place each particle in a
# dedicated list according to its distance to skeleton features
half_px = min(imp.getCalibration().pixelWidth, imp.getCalibration().pixelHeight) / 2
n_bp = n_tip = n_shaft = n_hybrid = 0
j_indices = []
ep_indices = []
jep_indices = []
slab_indices = []

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

    # Is particle associated with neither junctions nor end-points?
    if j_dist > cutoff_dist and ep_dist > cutoff_dist:
        slab_indices.append(i)
        n_shaft += 1
    # Is particle associated with both?
    elif abs(j_dist - ep_dist) <= half_px:
        jep_indices.append(i)
        n_hybrid += 1
    # Is particle associated with an end-point?
    elif ep_dist < j_dist:
        ep_indices.append(i)
        n_tip += 1
    # Is particle associated with a junction?
    elif ep_dist > j_dist:
        j_indices.append(i)
        n_bp += 1
    else:
        print(">>>Error labeling index", i)


# Now that we've split particles into groups, we should
# probably set a label image, but the procedure is simple
# enough that we can use traced ROIs directly
for i in range(n_particles):

    roi_name = ""
    roi_id = str(i).zfill(4);

    if i in jep_indices:
        roi_name = "Hybrid:" + roi_id
    elif i in j_indices:
        roi_name = "Junction:" + roi_id
    elif i in ep_indices:
        roi_name = "Tip:" + roi_id
    elif i in slab_indices:
        roi_name = "Shaft:" + roi_id
    else:
        roi_name = "UNKNOWN:" + roi_id

    rm.select(i)
    rm.runCommand("Rename", roi_name)

rm.runCommand(imp, "Show All")
rm.runCommand(impSkel, "Show None")

# Output some frequencies. We'll use some convenience
# functions to log everything to the IJ Log window
def ratio(n, total):
    return 0.0 if total is 0 else round(float(n)/total*100, 3)
def log(*arg):
    IJ.log("%s" % ''.join(map(str, arg)))

log("\n \n*** Stats for ", imp.getTitle(), " ***")
log("BP particles: ", n_bp, ", ", ratio(n_bp, n_particles), "%")
log("Tip particles: ", n_tip, ", ", ratio(n_tip, n_particles), "%")
log("Shaft particles: ", n_shaft, ", ", ratio(n_shaft, n_particles), "%")
log("Hybrid particles: ", n_hybrid, ", ", ratio(n_hybrid, n_particles), "%")
log("Occupied BPs: ", n_bp + n_hybrid, ", ", ratio(n_bp+n_hybrid, n_junctions), "%")
log("Occupied Tips: ", n_tip + n_hybrid, ", ", ratio(n_tip+n_hybrid, len(end_points)), "%")
log("\nCutoff distance: ", cutoff_dist, imp.getCalibration().getUnits())
log("Threshold range: ", threshold_lower, "-", threshold_upper)
log("Size range: ", size_min, "-", size_max)

# Our job is now done. We'll just measure tagged particles before exiting
if measure_rois:
    prev_measurements = Analyzer.getMeasurements()
    measurements = (M.AREA + M.MEAN + M.FERET + M.LIMIT + M.INTEGRATED_DENSITY + M.LABELS)
    Analyzer.setMeasurements(measurements)
    ResultsTable.getResultsTable().reset()
    rm.runCommand(imp, "Deselect")
    rm.runCommand(imp, "Measure")
    Analyzer.setMeasurements(prev_measurements) # Restore initial preferences

IJ.resetThreshold(imp);
