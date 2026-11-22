"""Matplotlib backend for PyIDE: plt.show() saves each figure as a PNG and sends it to the Plots tab."""
from matplotlib._pylab_helpers import Gcf
from matplotlib.backend_bases import FigureManagerBase, _Backend
from matplotlib.backends.backend_agg import FigureCanvasAgg

import runner


class FigureManagerPyIDE(FigureManagerBase):
    def show(self):
        runner.save_figure(self.canvas.figure)
        Gcf.destroy(self.num)


@_Backend.export
class _BackendPyIDE(_Backend):
    FigureCanvas = FigureCanvasAgg
    FigureManager = FigureManagerPyIDE
