#! /usr/bin/env python
"""
Simple Python test client for Brooklyn REST interface
"""

import os
import sys

import json
import requests
import argparse

from pprint import pprint

def main():
    parser = argparse.ArgumentParser(
        description="Deploy a Brooklyn application using the REST endpoint")

    parser.add_argument('--endpoint', type=str, default="http://localhost:8080",
        help="Brooklyn REST endpoint URL")
    parser.add_argument('--name', required=True, help="Application name")
    parser.add_argument('--entity', type=str,
        default="brooklyn.entity.nosql.redis.RedisStore", help="Entity name")
    parser.add_argument('--location', type=str, default="0", help="LocationSpec ID")

    args = parser.parse_args()

    target = args.endpoint + "/v1/applications"
    print "\nPOST %s\n" % target

    spec = dict(
        name=args.name,
        entities=[dict(name="ent1", type=args.entity, config=dict())],
        locations=["/v1/locations/" + args.location]
    )
    pprint(spec)

    response = requests.post(target, data=json.dumps(spec),
        headers={"content-type": "application/json"})

    print "\nServer Response:\n"
    pprint(dict(code=response.status_code, text=response.text))

if __name__ == '__main__':
    sys.exit(main())