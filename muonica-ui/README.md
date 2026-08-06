# Muonica UI

The frontend consumes the neutral Muonica JSON model. It renders Markdown, notices, generic diagram blocks,
and generated technical slots without becoming part of the Java library's internal model.

Diagram runtimes are optional and are not loaded from a CDN. Without a renderer, the UI keeps the original
diagram source visible.
