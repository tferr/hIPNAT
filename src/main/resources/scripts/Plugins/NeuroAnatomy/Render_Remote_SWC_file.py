# @String(value="This script renders a remote (e)SWC file (internet connection required)",visibility="MESSAGE") msg
# @String(label="URL to SWC file",description="Local files can also be loaded using a file URI (file:///absolute/path/to/file)",required=true, value="http://neuromorpho.org/dableFiles/de%20koninck/CNG%20version/OM46-Cell1.CNG.swc") input_url
# @Double(label="Scaling factor",description="Will be applied to all SWC coordinates",min=0.2,max=5.0,value=1.0) scale_factor
# @String(label="Render as", choices={"3D paths (monochrome)","3D paths (SWC-type colors)","3D paths (colored by path ID)","Untagged skeleton","Tagged skeleton","2D ROIs"}) rendering_choice
# @LogService log
# @StatusService status
# @UIService uiservice

'''
file:       Render_Remote_SWC_File.py
author:     Tiago Ferreira
version:    20170216
info:       Exemplifies how to load a remote SWC file
'''

from ipnat import ImportTracings
from ipnat import Utils


def get_rendering_choice(choice):
    return {
        '3D paths (monochrome)': ImportTracings.GRAY_3DVIEWER,
        '3D paths (SWC-type colors)': ImportTracings.COLOR_3DVIEWER,
        '3D paths (colored by path ID)': ImportTracings.COLORMAP_3DVIEWER,
        'Untagged skeleton': ImportTracings.COLORMAP_3DVIEWER,
        'Tagged skeleton': ImportTracings.TAGGED_SKEL,
        '2D ROIs': ImportTracings.ROI_PATHS,
    }.get(choice, ImportTracings.COLOR_3DVIEWER)


def error(msg):
    uiservice.showDialog(msg, "Error")


def run():

    IT = ImportTracings()
    if not IT.isSWCfile(input_url):
        error("Not a valid (e)SWC file:\n%s" % input_url)
        return
    status.showStatus('Loading %s...' % input_url)
    f = Utils.loadRemoteFileQuietly(input_url)
    if f is None:
        error("Could not load\n%s\nPerhaps you're offline?" % input_url)
        return;
    IT.applyScalingFactor(scale_factor, scale_factor, scale_factor)
    IT.autoLoadSWC(f, True);
    try:
        IT.renderPaths(get_rendering_choice(rendering_choice))
    except Exception, msg:
        log.error("An error occurred when rendering paths. Details:\n%s" % msg)



if __name__ == '__main__':
    run()
