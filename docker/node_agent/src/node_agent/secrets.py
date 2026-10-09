import boto3


def get_secret(secret_id: str, region: str, client_factory=boto3.client) -> str:
    """Returns the SecretString of a Secrets Manager secret; raises on any failure."""
    client = client_factory("secretsmanager", region_name=region)
    value = client.get_secret_value(SecretId=secret_id).get("SecretString")
    if not value:
        raise ValueError(f"Secret {secret_id} has no string value")
    return value
