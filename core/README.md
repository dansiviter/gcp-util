# Cloud Monitoring Utils. - Core #


## Monitored Resource ##

This library makes a best efforts to attempt to find suitable [Monitored Resource](https://cloud.google.com/monitoring/api/resources) type via `uk.dansiviter.gcp.MonitoredResourceProvider#monitoredResource()`. This can be overriden in two ways:
* Implement your own `MonitoredResourceProvider` using the Service Loader mechanism,
* If you only need to override a few labels you can either:
  * Specify the label via upper-case environment variable. e.g. `namespace_name` would become `NAMESPACE_NAME`,
	* Specify a System Property with a prefix of `gcp.cloud.resource.`. e.g. `namespace_name` would become `gcp.cloud.resource.namespace_name`.

> :information_source: Environment Parameters would override System Properties if both are specified.


### Google Kubernetes Engine ###

For GKE with [Workload Identity](https://cloud.google.com/kubernetes-engine/docs/how-to/workload-identity#gke_mds) enabled there are only two items that cannot be found: Container Name and Namespace. However, that can be over come with:

```yaml
...
spec:
  containers:
  - name: &containerName my-app  # using anchor to keep inline
	...
	env:
	- name: CONTAINER_NAME
	  value: *containerName
	- name: NAMESPACE_NAME
	  valueFrom:
		fieldRef:
		  fieldPath: metadata.namespace
...
```
