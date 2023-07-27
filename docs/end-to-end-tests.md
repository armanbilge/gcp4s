End to End Tests
================

In order to run the `EndToEndSuite` you need a GCP service account with at least the `roles/bigquery.user` role permissions.
You'll then want a JSON key for this service account stored in the evironment variable `SERVICE_ACCOUNT_CREDENTIALS`.

## Setup

1. Create a new service account
2. Grant service account access to project with `roles/bigquery.user` role (see image below)
3. Create a key with type JSON
4. Create ENV var with JSON key
  -  eg: `export SERVICE_ACCOUNT_CREDENTIALS=$(cat gcp-key.json) && sbt`

@:image(imgs/roles-bigquery-user.png) {
  intrinsicWidth = 1078
  intrinsicHeight = 904
  style = centerImg
  alt = Setting bigquery user role in GCP console
  title = BigQuery Permissions
}
