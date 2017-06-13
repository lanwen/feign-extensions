# Feign Extensions

Some useful feign extensions

## Extended Slf4J

Writes logs in format:

```
DEBUG feign.Logger - http	req-id=[701d8882-e43b-4af3-b5e5-7dc4b4cf4de7]   call=[someMethod()]	method=[GET]	uri=[http://api.example.com]
DEBUG feign.Logger - http	req-id=[701d8882-e43b-4af3-b5e5-7dc4b4cf4de7]	status=[200]	reason=[OK] elapsed-ms=[273]	length=[0]

```

Then you can grep such logs with `| cut -f 3` (with right column number)

Also this logger adds unique `req-id` string to merge request and response.
All retries and error log lines have such string too.
This string is changed each request.
