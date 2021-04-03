# Microprofile Config GCP Integration

## Secret Manager ##

Supports 3 formats of secret naming:
* `secrets/{secret}` - This will default to current `projectId` and `latest` version,
* `secrets/{secret}/versions/{version}` - This will default to current `projectId`,
* `projects/{project}/secrets/{secret}/versions/{version}` - Standard GCP naming convention.
