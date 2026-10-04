#!/usr/bin/env python3
"""Fail CI if a required boundary suite is excluded, empty, skipped, or failing."""
import sys
from pathlib import Path
from xml.etree import ElementTree

REQUIRED = {
    'directory-service': {'com.saas.directory.DirectoryContextBootSmokeTest'},
    'product-service': {
        'com.portcelana.natiart.ProductContextBootSmokeTest',
        'com.portcelana.natiart.ServiceConstructorWiringTest',
        'com.portcelana.natiart.service.OrderCommittedContractTest',
        'com.portcelana.natiart.service.PaymentHttpCommitContractTest',
        'com.portcelana.natiart.controller.ProductDetachedHttpContractTest',
    },
}


def main():
    service = sys.argv[1]
    reports = Path(__file__).resolve().parents[1] / service / 'build/test-results/test'
    for name in sorted(REQUIRED[service]):
        path = reports / f'TEST-{name}.xml'
        suite = ElementTree.parse(path).getroot()
        if int(suite.attrib['tests']) < 1 or any(int(suite.attrib.get(key, '0')) for key in ('failures', 'errors', 'skipped')):
            raise SystemExit(f'Required boundary suite did not pass in full: {name}')
        print(f'Boundary report verified: {name} ({suite.attrib["tests"]} tests)')


if __name__ == '__main__':
    main()
