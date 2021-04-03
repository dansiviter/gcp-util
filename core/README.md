# Cloud Monitoring Utils. - Core #


## Monitored Resource ##

This library makes a best efforts to attempt to find suitable [Monitored Resource](https://cloud.google.com/monitoring/api/resources) type via `uk.dansiviter.gcp.MonitoredResourceProvider#monitoredResource()`. See the JavaDoc for that class for further information on how to override.

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
