"""Unit tests for the Klipper extras the app ships in beam_ext.

Run from the repository root:
    python3 -m unittest discover -s app/src/test/python -v
"""
import filecmp
import importlib.util
import logging
import os
import unittest
from unittest import mock

ASSETS = os.path.join(os.path.dirname(__file__), '..', '..', 'main', 'assets')


def load(engine, name):
    path = os.path.join(ASSETS, engine, 'beam_ext', name + '.py')
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class FakeGcode:
    def __init__(self):
        self.commands = {}

    def register_command(self, name, func, desc=None):
        self.commands[name] = (func, desc)


class FakePrinter:
    def __init__(self):
        self.gcode = FakeGcode()

    def lookup_object(self, name):
        return self.gcode


class FakeConfig:
    def __init__(self):
        self.printer = FakePrinter()

    def get_printer(self):
        return self.printer


class Gcmd:
    def __init__(self, **params):
        self.params = params

    def get(self, name, default=None):
        return self.params[name] if name in self.params else default

    def get_int(self, name):
        return int(self.params[name])

    def get_float(self, name):
        return float(self.params[name])


class FakeResponse:
    def read(self):
        return b'{"ok": true}'

    def close(self):
        pass


class Extras(unittest.TestCase):
    engines = ('klipper', 'kalico')

    def urls(self, engine, module_name, command, **params):
        module = load(engine, module_name)
        extra = module.load_config(FakeConfig())
        func = extra.gcode.commands[command][0]
        with mock.patch.object(module.urllib.request, 'urlopen',
                               return_value=FakeResponse()) as urlopen:
            func(Gcmd(**params))
        return [c.args[0] for c in urlopen.call_args_list]


class Beeper(Extras):
    def test_registers_play_tone(self):
        for engine in self.engines:
            extra = load(engine, 'beam_beeper').load_config(FakeConfig())
            self.assertEqual(list(extra.gcode.commands), ['PLAY_TONE'])

    def test_play_tone_sends_duration_and_frequency(self):
        for engine in self.engines:
            urls = self.urls(engine, 'beam_beeper', 'PLAY_TONE',
                             DURATION='250', FREQUENCY='440')
            self.assertEqual(len(urls), 1)
            self.assertIn('/beam/play_tone?', urls[0])
            self.assertIn('duration=250', urls[0])
            self.assertIn('frequency=440', urls[0])

    def test_a_failed_request_is_logged_not_raised(self):
        module = load('klipper', 'beam_beeper')
        extra = module.load_config(FakeConfig())
        func = extra.gcode.commands['PLAY_TONE'][0]
        with mock.patch.object(module.urllib.request, 'urlopen',
                               side_effect=OSError('refused')):
            with self.assertLogs(level=logging.ERROR):
                func(Gcmd(DURATION='1', FREQUENCY='1'))


class Camera(Extras):
    def test_registers_flashlight_and_focus(self):
        for engine in self.engines:
            extra = load(engine, 'beam_camera').load_config(FakeConfig())
            self.assertEqual(sorted(extra.gcode.commands),
                             ['SET_CAMERA_FLASHLIGHT', 'SET_CAMERA_FOCUS'])

    def test_flashlight_passes_the_state_through(self):
        for engine in self.engines:
            urls = self.urls(engine, 'beam_camera', 'SET_CAMERA_FLASHLIGHT',
                             ENABLED='true')
            self.assertEqual(len(urls), 1)
            self.assertIn('/beam/set_camera_flashlight?enabled=true', urls[0])

    def test_autofocus_ignores_the_distance(self):
        for engine in self.engines:
            urls = self.urls(engine, 'beam_camera', 'SET_CAMERA_FOCUS',
                             AUTOFOCUS='True')
            self.assertIn('autofocus=true', urls[0])
            self.assertIn('focus=0', urls[0])

    def test_manual_focus_sends_the_distance(self):
        for engine in self.engines:
            urls = self.urls(engine, 'beam_camera', 'SET_CAMERA_FOCUS',
                             AUTOFOCUS='false', FOCUS_DISTANCE='2.5')
            self.assertIn('autofocus=false', urls[0])
            self.assertIn('focus=2.5', urls[0])

    def test_a_failed_request_is_logged_not_raised(self):
        module = load('klipper', 'beam_camera')
        extra = module.load_config(FakeConfig())
        func = extra.gcode.commands['SET_CAMERA_FLASHLIGHT'][0]
        with mock.patch.object(module.urllib.request, 'urlopen',
                               side_effect=OSError('refused')):
            with self.assertLogs(level=logging.ERROR):
                func(Gcmd(ENABLED='true'))


class Engines(unittest.TestCase):
    def test_klipper_and_kalico_ship_the_same_extras(self):
        for name in ('beam_beeper.py', 'beam_camera.py'):
            self.assertTrue(filecmp.cmp(
                os.path.join(ASSETS, 'klipper', 'beam_ext', name),
                os.path.join(ASSETS, 'kalico', 'beam_ext', name),
                shallow=False), name)


class KnownIssues(Extras):
    # The extras call the app's web server on a fixed port, but it listens on
    # the port of the active front end (4408 Fluidd, 4409 Mainsail, 4410
    # Voyager UI). Nothing answers on 8888, so M300 / SET_CAMERA_* only log
    # an error. Remove the decorator when the extras reach the real server.
    @unittest.expectedFailure
    def test_extras_call_a_port_the_app_serves(self):
        served = {4408, 4409, 4410}
        for module, command, params in (
                ('beam_beeper', 'PLAY_TONE', dict(DURATION='1', FREQUENCY='1')),
                ('beam_camera', 'SET_CAMERA_FLASHLIGHT', dict(ENABLED='true'))):
            url = self.urls('klipper', module, command, **params)[0]
            port = int(url.split('//')[1].split('/')[0].split(':')[1])
            self.assertIn(port, served, module)


if __name__ == '__main__':
    unittest.main()
