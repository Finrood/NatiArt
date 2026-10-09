FROM python:3.13-alpine
WORKDIR /qa
RUN addgroup -S qa && adduser -S qa -G qa && mkdir /state && chown qa:qa /state
COPY qa/provider.py qa/history-payments.json qa/customers.json /qa/
COPY qa/assets /qa/assets
USER qa
EXPOSE 8090
HEALTHCHECK --interval=5s --timeout=3s --start-period=5s CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8090/healthz', timeout=2)" || exit 1
CMD ["python", "-u", "/qa/provider.py"]
