# Reports

The Reports API demonstrates two common documentation patterns: a fast aggregate read and a long-running export workflow. The latter returns a job immediately and lets clients poll until a file is ready.

Report dates are inclusive and use calendar days in UTC. Export formats are intentionally small in this demo, but the job model leaves room for additional formats and delivery destinations.

:::notice info
Report jobs are kept in memory. A restart clears queued and completed exports.
:::

:::slot security
:::
