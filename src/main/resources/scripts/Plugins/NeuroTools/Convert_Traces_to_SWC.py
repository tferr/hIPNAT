# @String(value="This script converts all .traces files in a directory into SWC. Conversion log will be shown in Console.", visibility="MESSAGE") msg
# @File(label="Directory of .traces files:", style="directory") input_dir
# @Boolean(label="Open Console", value=false) open_console
# @LogService log
# @StatusService status
# @UIService ui

'''
file:       Convert_Traces_to_SWC.py
author:     Mark Longair / Tiago Ferreira
version:    20170203
info:       Converts all .traces files in a directory into SWC.
            Based on https://gist.github.com/mhl/888051
'''

import os, re
from tracing import PathAndFillManager

def run():
    if not input_dir:
        return
    status.showStatus("Converting .traces files...")
    if open_console:
        ui.getDefaultUI().getConsolePane().show()
    conversion_counter = 0
    d = str(input_dir)
    log.info('Processing %s...' % d)
    for e in os.listdir(d):
        if os.path.basename(e).startswith('.'):
            continue
        if not e.endswith('.traces'):
            log.info('Skipping ' + e + '...')
            continue
        traces_filename = os.path.join(d, e)
        swc_filename_prefix = re.sub(r'\.traces', '-exported', traces_filename)
        log.info('Converting %s to %s-*.swc' % (traces_filename, swc_filename_prefix))
        pafm = PathAndFillManager()
        try:
            pafm.loadGuessingType(traces_filename)
            if pafm.checkOKToWriteAllAsSWC(swc_filename_prefix):
                pafm.exportAllAsSWC(swc_filename_prefix)
                conversion_counter += 1
            else:
                raise IOError
        except IOError:
            log.error('Could not convert ' + traces_filename)
    log.info(str(conversion_counter) + ' file(s) successfully converted')

if __name__ == '__main__':
    run()
