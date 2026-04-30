# Landing page

Static landing page for the home domain, intended to be served by nginx.

Routes:

- `/monitor/` — connectivity-monitor
- `/top/` — top-monitor
- `/speedtest/` — speedtest

Example nginx root:

```nginx
server {
    server_name home.edubovit.net;

    root /opt/home-landing;
    index index.html;

    location = / {
        try_files /index.html =404;
    }

    location /monitor/ {
        proxy_pass http://127.0.0.1:20001/;
    }

    location /top/ {
        proxy_pass http://127.0.0.1:20002/;
    }

    location /speedtest/ {
        proxy_pass http://127.0.0.1:20003/;
    }
}
```

Adjust upstream ports to match the deployed applications.
