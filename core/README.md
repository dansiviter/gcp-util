# Cloud Monitoring Utils. - Core #


## Monitored Resource ##

This library makes a best efforts to attempt to find suitable [Monitored Resource](https://cloud.google.com/monitoring/api/resources) type via `uk.dansiviter.gcp.ResourceType#autoDetect()`. These values will first attempt to find these from Environment Variables in uppercase then try using prefix `google.cloud.resource.` from System Properties. Some will also have some other special ways of extracting, but you'll have to refer to the code for more information. e.g. for `project_id` will try:
* `PROJECT_ID` from Environment Variables,
* `google.cloud.resource.project_id` from System Properties,
* This will then try `ServiceOptions#getDefaultProjectId()`.

For GKE with [Workload Identity](https://cloud.google.com/kubernetes-engine/docs/how-to/workload-identity#gke_mds) enabled there are only two items that cannot be found: Container Name and Namespace. However, that can be over come with:

```yaml
...
spec:
  containers:
  - name: &containerNameN my-app  # using anchor to keep inline
	...
	env:
	- name: CONTAINER_NAME
	  value: *containerNameN
	- name: NAMESPACE_NAME
	  valueFrom:
		fieldRef:
		  fieldPath: metadata.namespace
...
```
