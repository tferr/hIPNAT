import os
import re
from tracing import PathAndFillManager

# An example script showing how to convert all the .traces
# files in a directory to SWC files.  (The .traces format
# is the native file format of Simple Neurite Tracer.)

def run():
    d = IJ.getDirectory("Choose your directory of .traces files...")
    if not d:
        return
    for e in os.listdir(d):
        if not e.endswith(".traces"):
            continue
        traces_filename = os.path.join(d,e)
        swc_filename_prefix = re.sub(r'\.traces', '-exported', traces_filename)
        IJ.log("Converting %s to %s-*.swc" % (traces_filename,
                                              swc_filename_prefix))
        pafm = PathAndFillManager()
        pafm.loadGuessingType(traces_filename)
        if pafm.checkOKToWriteAllAsSWC(swc_filename_prefix):
            pafm.exportAllAsSWC(swc_filename_prefix)

run()
