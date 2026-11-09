#!/usr/bin/env python3
"""
Fills the store with the products in catalog.json, using the app's own REST API:
it logs in as the admin, creates any missing categories, creates each product that
doesn't exist yet, then downloads the product's photo and uploads it.

Products that already exist (same name) are skipped, so it's safe to run again.
Uses only the Python standard library, so it runs on a fresh Ubuntu server.

Usage (deploy/demo-setup.sh runs this on the server for you):
    ADMIN_PASSWORD=... python3 seed_catalog.py --api http://127.0.0.1 --catalog catalog.json
"""
import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid


class Api:
    """A tiny client for the store's API that keeps the login cookie."""

    def __init__(self, base_url):
        self.base = base_url.rstrip("/") + "/api"
        self.cookie = None

    def call(self, method, path, body=None, headers=None):
        headers = dict(headers or {})
        if isinstance(body, (dict, list)):
            body = json.dumps(body).encode()
            headers["Content-Type"] = "application/json"
        if self.cookie:
            headers["Cookie"] = self.cookie
        request = urllib.request.Request(self.base + path, data=body, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return response.status, self._json(response.read()), response.headers
        except urllib.error.HTTPError as error:
            return error.code, self._json(error.read()), error.headers

    @staticmethod
    def _json(raw):
        try:
            return json.loads(raw)
        except ValueError:
            return {}

    def login(self, username, password):
        status, data, headers = self.call("POST", "/auth/signin", {"username": username, "password": password})
        if status != 200:
            sys.exit(f"Login as {username} failed ({status}): {data.get('message', 'check the password')}")
        # The JWT comes back as a cookie; keep just "name=value" and send it on every call
        self.cookie = headers.get("Set-Cookie", "").split(";")[0]


def download(url):
    request = urllib.request.Request(url, headers={"User-Agent": "sb-ecom-seed/1.0"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read(), response.headers.get_content_type()


def multipart(field, filename, content, content_type):
    """Builds a multipart/form-data body with one file, like a browser upload does."""
    boundary = uuid.uuid4().hex
    body = (f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="{field}"; filename="{filename}"\r\n'
            f"Content-Type: {content_type}\r\n\r\n").encode() + content + f"\r\n--{boundary}--\r\n".encode()
    return body, {"Content-Type": f"multipart/form-data; boundary={boundary}"}


def slug(name):
    return "".join(c if c.isalnum() else "-" for c in name.lower()).strip("-")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--api", default="http://127.0.0.1", help="address of the store (default: %(default)s)")
    parser.add_argument("--catalog", default=os.path.join(os.path.dirname(__file__), "catalog.json"))
    parser.add_argument("--username", default="admin")
    parser.add_argument("--check-login", action="store_true", help="only check that the password works")
    args = parser.parse_args()

    password = os.environ.get("ADMIN_PASSWORD") or sys.exit("Set ADMIN_PASSWORD to the admin's password")

    api = Api(args.api)
    api.login(args.username, password)
    if args.check_login:
        return

    products = json.load(open(args.catalog))["products"]

    # Categories: the API answers 400 when there are none yet, so treat that as an empty list
    status, data, _ = api.call("GET", "/public/categories?pageSize=1000")
    category_ids = {c["categoryName"]: c["categoryId"] for c in data.get("content", [])} if status == 200 else {}
    for name in sorted({p["category"] for p in products} - set(category_ids)):
        status, data, _ = api.call("POST", "/admin/categories", {"categoryName": name})
        if status != 201:
            sys.exit(f"Couldn't create category {name} ({status}): {data}")
        category_ids[name] = data["categoryId"]
        print(f"+ category {name}")

    status, data, _ = api.call("GET", "/public/products?pageSize=1000")
    existing = {p["productName"] for p in data.get("content", [])}

    created = skipped = without_photo = 0
    for p in products:
        if p["name"] in existing:
            skipped += 1
            continue
        special_price = round(p["price"] * (1 - p["discount"] / 100), 2)
        status, data, _ = api.call("POST", f"/admin/categories/{category_ids[p['category']]}/product", {
            "productName": p["name"], "description": p["description"], "quantity": p["quantity"],
            "price": p["price"], "discount": p["discount"], "specialPrice": special_price,
        })
        if status != 201:
            print(f"! couldn't create {p['name']} ({status}): {data.get('message', data)}")
            continue
        created += 1

        # Try the full-size photo first, then the smaller thumbnail (uploads are capped at 5 MB)
        uploaded = False
        for url in (p["image"], p.get("fallbackImage")):
            if not url:
                continue
            try:
                content, content_type = download(url)
            except Exception as error:  # network problems shouldn't stop the whole run
                print(f"! couldn't download {url}: {error}")
                continue
            extension = os.path.splitext(urllib.parse.urlparse(url).path)[1] or ".jpg"
            body, headers = multipart("image", slug(p["name"]) + extension, content, content_type)
            status, _, _ = api.call("PUT", f"/admin/products/{data['productId']}/image", body, headers)
            if status == 200:
                uploaded = True
                break
            print(f"! photo upload for {p['name']} failed ({status})")
        if not uploaded:
            without_photo += 1
        print(f"+ {p['category']}: {p['name']}" + ("" if uploaded else " (no photo)"))

    print(f"\nDone: {created} products added, {skipped} already there, {without_photo} without a photo.")
    if without_photo:
        sys.exit(1)


if __name__ == "__main__":
    main()
