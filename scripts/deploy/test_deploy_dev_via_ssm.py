import importlib.util
import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


SCRIPT = Path(__file__).with_name("deploy-dev-via-ssm.py")
SPEC = importlib.util.spec_from_file_location("deploy_dev_via_ssm", SCRIPT)
deploy_script = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(deploy_script)


class DeploymentTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        root = Path(self.temp_dir.name)
        self.jar = root / "application.jar"
        self.release_script = root / "release-dev-service.sh"
        self.jar.write_bytes(b"jar contents")
        self.release_script.write_text("#!/bin/bash\nexit 0\n")
        self.calls = []
        self.commit = "a" * 40

    def fake_aws(self, *arguments):
        self.calls.append(arguments)
        if arguments[:2] == ("ssm", "send-command"):
            return f"command-{sum(call[:2] == ('ssm', 'send-command') for call in self.calls)}"
        if arguments[:2] == ("ssm", "get-command-invocation"):
            return json.dumps({"Status": "Success"})
        return ""

    def run_deployment(self):
        deploy_script.deploy(
            self.jar, self.release_script, "geupjido-dev-releases",
            "i-0123456789abcdef0", "ap-northeast-2", self.commit,
        )

    def test_success_uploads_verified_files_and_sends_one_command(self):
        with patch.object(deploy_script, "aws", side_effect=self.fake_aws), \
                patch.object(deploy_script, "verify_public_api"):
            self.run_deployment()

        uploads = [call for call in self.calls if call[:2] == ("s3", "cp")]
        commands = [call for call in self.calls if call[:2] == ("ssm", "send-command")]
        self.assertEqual(2, len(uploads))
        self.assertEqual(1, len(commands))
        parameters = json.loads(commands[0][commands[0].index("--parameters") + 1])
        remote = parameters["commands"][0]
        self.assertIn("sha256sum --check --status", remote)
        self.assertIn("release-dev-service.sh\" deploy", remote)
        self.assertIn(f"dev-releases/{self.commit}/application.jar", remote)

    def test_failed_public_check_runs_rollback_and_reports_failure(self):
        with patch.object(deploy_script, "aws", side_effect=self.fake_aws), \
                patch.object(deploy_script, "verify_public_api", side_effect=RuntimeError("health 실패")):
            with self.assertRaisesRegex(RuntimeError, "이전 JAR 복구"):
                self.run_deployment()

        commands = [call for call in self.calls if call[:2] == ("ssm", "send-command")]
        self.assertEqual(2, len(commands))
        rollback_parameters = json.loads(commands[1][commands[1].index("--parameters") + 1])
        self.assertIn("release-dev-service.sh\" rollback", rollback_parameters["commands"][0])

    def test_remote_commands_have_valid_shell_syntax(self):
        checksum = "b" * 64
        commands = (
            deploy_script.remote_deploy_command(
                "geupjido-dev-releases", self.commit, "ap-northeast-2", checksum, checksum
            ),
            deploy_script.remote_rollback_command(
                "geupjido-dev-releases", self.commit, "ap-northeast-2", checksum
            ),
        )
        for command in commands:
            with self.subTest(command=command.splitlines()[-1]):
                result = subprocess.run(["sh", "-n"], input=command, text=True, capture_output=True)
                self.assertEqual(0, result.returncode, result.stderr)


if __name__ == "__main__":
    unittest.main()
