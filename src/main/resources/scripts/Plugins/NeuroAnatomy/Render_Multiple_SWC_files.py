# @String(value="This script renders all SWC files in a directory in a common canvas",visibility="MESSAGE") msg
# @File(label="Directory of SWC files", style="directory") input_dir
# @String(label="Filenames containing",description="Clear field for no filtering",value="") filenameFilter
# @Double(label="Scaling factor",description="Will be applied to all SWC coordinates",min=0.2,max=5.0,value=1.0) scale_factor
# @String(label="Render as", choices={"3D viewer objects","Skeletons","Projected skeletons"}, style="radioButtonHorizontal") display_choice
# @LogService log
# @StatusService status

'''
file:       Render_Multiple_SWC_Files.py
author:     Tiago Ferreira
version:    20170206
info:       Renders a series of SWC files in a single ImageWindow/ImageWindow3D
'''

import os
from ipnat import ImportTracings
from ij import IJ, ImagePlus
from java.lang import Exception


def getFileList(directory, filteringString):
    """Returns a list containing the file paths in the specified directory
        path. The list is recursive (includes subdirectories) and will only
        include files whose filename contains the specified string."""
    files = []
    for (dirpath, dirnames, filenames) in os.walk(directory):
        for f in filenames:
            if filteringString in f:
                files.append(os.path.join(dirpath, f))
    return files


def run():

    IT = ImportTracings()
    IT.applyScalingFactor(scale_factor, scale_factor, scale_factor)
    # IT.applyCalibration(1, 1, 1, "um")
    proj_rendering = "Projected" in display_choice

    d = str(input_dir)
    files = getFileList(d, filenameFilter);
    for (counter, f) in enumerate(files):
        basename = os.path.basename(f)
        if basename.startswith('.') or not IT.isSWCfile(f):
            continue
        status.showStatus('Loading file %s: %s...' % (counter+1, basename))
        try:
            IT.loadSWCfile(f, proj_rendering)
            if proj_rendering:
                imp = ImagePlus("file: "+basename, IT.getRenderedProjection())
                imp.show()
        except Exception, msg:  # Jython 3: except Exception as msg:
            log.error("An error occurred when loading %s. Details:\n%s" % (f, msg))
            break

    try:
        title = "SWC files in %s (%sx)" % (os.path.basename(d), scale_factor)
        if proj_rendering:
            IJ.run("Images to Stack",
                  ("method=[Copy (center)] name=[%s] title=[file: ] use" % title))
        elif "3D" in display_choice:
            univ = IT.getNewUniverse()
            IT.renderPathsIn3DViewer(IT.COLOR_3DVIEWER, univ)
            univ.show()
            univ.getWindow().setTitle(title)
        else:
            imp = IT.renderPathVolume(False)
            imp.setTitle(title)
            imp.show()
    except Exception, msg:
        log.error("An error occurred when rendering paths. Details:\n%s" % msg)


if __name__ == '__main__':
    run()
