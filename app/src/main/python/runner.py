import sys, io, os, re, json, zipfile, shutil, threading, builtins, ctypes, traceback, urllib.request, codeop
import jedi
from pyflakes import api as pyflakes_api

SITE = None
PLOT_DIR = None
CANCEL = "__PYIDE_CANCEL__"  # must match Bridge.CANCEL in Kotlin
_tid = None
_bridge = None
_plot_n = 0
_namespace = {"__name__": "__main__"}
_console_buffer = ""
_execution_lock = threading.RLock()


def init(site_dir, plot_dir, mpl_dir):
    global SITE, PLOT_DIR
    SITE, PLOT_DIR = site_dir, plot_dir
    for d in (site_dir, plot_dir, mpl_dir):
        os.makedirs(d, exist_ok=True)
    os.environ["MPLCONFIGDIR"] = mpl_dir
    os.environ["MPLBACKEND"] = "module://plotbackend"
    if site_dir not in sys.path:
        sys.path.insert(0, site_dir)


# ---------- plots ----------
def save_figure(fig):
    global _plot_n
    _plot_n += 1
    path = os.path.join(PLOT_DIR, "plot_%d.png" % _plot_n)
    fig.savefig(path, dpi=110, bbox_inches="tight")
    if _bridge is not None:
        _bridge.plot(path)


def _clear_plots():
    for f in os.listdir(PLOT_DIR):
        try:
            os.remove(os.path.join(PLOT_DIR, f))
        except OSError:
            pass


# ---------- stdin / stdout ----------
class _Out:
    def __init__(self, bridge):
        self.b, self.buf = bridge, []
        self.encoding = "utf-8"

    def write(self, t):
        if not t:
            return 0
        self.buf.append(t)
        if "\n" in t:
            self.flush()
        return len(t)

    def flush(self):
        if self.buf:
            text, self.buf = "".join(self.buf), []
            self.b.write(text)

    def isatty(self):
        return False


class _In:
    def __init__(self, bridge, out):
        self.b, self.out = bridge, out

    def readline(self, size=-1):
        self.out.flush()
        line = self.b.readLine("")
        if line == CANCEL:
            raise KeyboardInterrupt
        return line + "\n"

    def read(self, size=-1):
        return self.readline()

    def __iter__(self):
        return self

    def __next__(self):
        return self.readline()

    def isatty(self):
        return False


def _make_input(bridge, out):
    def _input(prompt=""):
        out.flush()
        line = bridge.readLine(str(prompt))
        if line == CANCEL:
            raise KeyboardInterrupt
        return line
    return _input


# ---------- run / stop ----------
def _attach_console(bridge):
    out = _Out(bridge)
    saved = (sys.stdout, sys.stderr, sys.stdin, builtins.input)
    sys.stdout = sys.stderr = out
    sys.stdin = _In(bridge, out)
    builtins.input = _make_input(bridge, out)
    return out, saved


def _restore_console(out, saved):
    out.flush()
    sys.stdout, sys.stderr, sys.stdin, builtins.input = saved


def run(code, workdir, bridge):
    global _tid, _bridge
    with _execution_lock:
        _bridge, _tid = bridge, threading.get_ident()
        _clear_plots()
        out, saved = _attach_console(bridge)
        try:
            os.chdir(workdir)
            if workdir not in sys.path:
                sys.path.insert(0, workdir)
            _namespace["__file__"] = "<editor>"
            exec(compile(code, "<editor>", "exec"), _namespace, _namespace)
            # Save figures which remain open if the backend did not handle plt.show().
            try:
                from matplotlib._pylab_helpers import Gcf
                for manager in list(Gcf.get_all_fig_managers()):
                    try:
                        save_figure(manager.canvas.figure)
                    except Exception:
                        traceback.print_exc()
                    finally:
                        Gcf.destroy(manager.num)
            except ImportError:
                pass
        except SystemExit:
            pass
        except KeyboardInterrupt:
            out.write("\n[stopped]\n")
        except BaseException as e:
            traceback.print_exception(type(e), e, e.__traceback__.tb_next)
        finally:
            _restore_console(out, saved)
            _tid = None


def console(code, workdir, bridge):
    """Execute one interactive-console submission in the same globals as editor scripts."""
    global _bridge, _console_buffer
    with _execution_lock:
        _bridge = bridge
        out, saved = _attach_console(bridge)
        try:
            os.chdir(workdir)
            if workdir not in sys.path:
                sys.path.insert(0, workdir)
            source = _console_buffer + code + "\n"
            try:
                compiled = codeop.compile_command(source, "<console>", "single")
            except (SyntaxError, OverflowError, ValueError):
                _console_buffer = ""
                raise
            if compiled is None:
                _console_buffer = source
                return False
            _console_buffer = ""
            eval(compiled, _namespace, _namespace)
            return True
        except SystemExit:
            return True
        except KeyboardInterrupt:
            out.write("\nKeyboardInterrupt\n")
            _console_buffer = ""
            return True
        except BaseException:
            traceback.print_exc()
            _console_buffer = ""
            return True
        finally:
            _restore_console(out, saved)


def stop():
    tid = _tid
    if tid is not None:
        ctypes.pythonapi.PyThreadState_SetAsyncExc(ctypes.c_ulong(tid), ctypes.py_object(KeyboardInterrupt))


# ---------- editor helpers ----------
def complete(code, line, col):
    try:
        res = jedi.Script(code).complete(line, col)[:30]
        return json.dumps([[c.name, c.complete or ""] for c in res])
    except Exception:
        return "[]"


class _Reporter:
    def __init__(self):
        self.items = []

    def unexpectedError(self, filename, msg):
        self.items.append([1, 0, str(msg)])

    def syntaxError(self, filename, msg, lineno, offset, text):
        self.items.append([lineno or 1, offset or 0, "SyntaxError: " + str(msg)])

    def flake(self, m):
        self.items.append([m.lineno, m.col, m.message % m.message_args])


def lint(code):
    r = _Reporter()
    pyflakes_api.check(code, "<editor>", r)
    return json.dumps(r.items)


# ---------- packages ----------
def bundled():
    from importlib import metadata
    names = ["numpy", "pandas", "scipy", "matplotlib", "seaborn", "scikit-learn", "statsmodels",
             "pillow", "torch", "tflite-runtime", "tensorflow"]
    out = []
    for n in names:
        try:
            out.append("%s %s" % (n, metadata.version(n)))
        except Exception:
            out.append("%s -" % n)
    return "Bundled libraries (- = not in this build):\n" + "\n".join(out)


def pip_install(name):
    log = []
    _install(name, log, set())
    return "\n".join(log)


def _install(spec, log, seen):
    key = re.split(r"[<>=!~;\[\s(]", spec.strip())[0].lower()
    if not key or key in seen:
        return
    seen.add(key)
    try:
        with urllib.request.urlopen("https://pypi.org/pypi/%s/json" % key, timeout=30) as r:
            info = json.load(r)
    except Exception as e:
        log.append("x %s: %s" % (key, e))
        return
    wheel = next((u for u in info["urls"] if u["filename"].endswith("-none-any.whl")), None)
    if not wheel:
        log.append("x %s: no pure-Python wheel (native packages go in app/build.gradle.kts)" % key)
        return
    data = urllib.request.urlopen(wheel["url"], timeout=120).read()
    zipfile.ZipFile(io.BytesIO(data)).extractall(SITE)
    log.append("ok %s %s" % (key, info["info"]["version"]))
    for req in info["info"].get("requires_dist") or []:
        if ";" not in req:
            _install(req, log, seen)
