# Microprofile Config GCP Integration

## MetaData ##

A few values values are able to be extracted from the MetaData Server:
* `gcp.project-id` - Default project identifier,
* `gcp.cluster-name` - Cluster name,
* `gcp.cluster-location` - Cluster location,
* `gcp.default-sa.email` - Default service account email.

These are useful when using property expressions to avoid repetition. For example, for Cloud SQL instances are in the same project: `${gcp.project-id}:REGION:INSTANCE-ID`

## Secret Manager ##

By default, this is not enabled. To enable leverage the Service Loader mechanism via `META-INF/services/org.eclipse.microprofile.config.spi.ConfigSource`.

Three formats of secret naming are supported:
* `secrets/{secret}` - This will default to current `projectId` and `latest` version,
* `secrets/{secret}/versions/{version}` - This will default to current `projectId`,
* `projects/{project}/secrets/{secret}/versions/{version}` - Standard GCP naming convention.
