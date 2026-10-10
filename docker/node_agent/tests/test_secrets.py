import unittest
from unittest import mock

from node_agent.secrets import get_secret
from tests.fakes import bind


def boto3_client(service_name, region_name=None, **kwargs):
    """Signature of `boto3.client`, used to compare factory calls."""


class GetSecretTest(unittest.TestCase):
    """`agent.run` reads the Tailscale key and the bot token with `get_secret` (§5 steps 2-3)."""

    def setUp(self):
        self.client = mock.Mock()
        self.client.get_secret_value.return_value = {"SecretString": "tskey-123", "Name": "ts-secret"}
        self.client_factory = mock.Mock(return_value=self.client)

    def test_returns_secret_string(self):
        """AC-P6 (support): get_secret returns the SecretString"""
        self.assertEqual("tskey-123", get_secret("ts-secret", "eu-central-1", client_factory=self.client_factory))

    def test_uses_secrets_manager_in_given_region(self):
        """AC-P6 (support): a Secrets Manager client is created for the given region"""
        get_secret("ts-secret", "eu-central-1", client_factory=self.client_factory)

        args, kwargs = self.client_factory.call_args
        self.assertEqual({"service_name": "secretsmanager", "region_name": "eu-central-1", "kwargs": {}},
                         bind(boto3_client, args, kwargs))

    def test_requests_given_secret_id(self):
        """AC-P6 (support): GetSecretValue is called with the given secret id"""
        get_secret("ts-secret", "eu-central-1", client_factory=self.client_factory)

        self.client.get_secret_value.assert_called_once_with(SecretId="ts-secret")


if __name__ == "__main__":
    unittest.main()
