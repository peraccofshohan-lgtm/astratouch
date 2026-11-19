#!/usr/bin/env python3
"""
Deterministic debug-signing keystore generator for AstraTouch (personal use).

WHY THIS EXISTS
---------------
Android requires every update of an installed app to be signed with the *same*
key. A runner-generated Android debug keystore (`~/.android/debug.keystore`) is
created fresh on every CI job, so each workflow run would produce an APK that
cannot be installed over the previous one — the classic "App not installed"
error after an update.

This script derives a signing key deterministically from a seed, so every run
produces a byte-identical private key and therefore an APK that upgrades cleanly.

SECURITY MODEL — READ THIS
--------------------------
This is a **debug/personal-use** signing identity. It is NOT a trust anchor:
  * It is not a production release key and confers no security guarantee.
  * The key is derived from a seed that is not a secret (see DEFAULT_SEED). It
    must never be used for anything distributed to other people, uploaded to a
    store, or used to protect user data.
  * No private key file is committed to the repository. The keystore is generated
    on demand in the runner's temp directory and discarded.
  * To make the identity private as well as stable, set ASTRA_DEBUG_SEED to a
    secret. Anyone who knows the seed can re-derive the key.

Determinism: same seed -> same EC private key -> same certificate serial, subject
and validity -> same PKCS#12 bytes (PKCS#12 uses random salts internally, so the
*file* bytes differ; the key and certificate do not, which is all Android checks).

Usage:
    python3 scripts/derive-debug-keystore.py --out /tmp/debug.p12
    ASTRA_DEBUG_SEED=my-secret python3 scripts/derive-debug-keystore.py --out ...

Exit codes: 0 success, 1 failure.
"""

from __future__ import annotations

import argparse
import hashlib
import os
import sys
from datetime import datetime, timedelta, timezone

DEFAULT_SEED = "astratouch-personal-use-debug-signing-v1"

# Fixed, documented credentials. A debug keystore's password protects nothing —
# the key is derivable from the seed — so it is published rather than pretended
# to be secret. Android tooling requires non-empty values.
STORE_PASSWORD = "astratouchdebug"
KEY_ALIAS = "astratouchdebug"
SUBJECT_CN = "CN=AstraTouch Debug"
SUBJECT_O = "O=AstraTouch Personal"
SUBJECT_C = "C=BD"
VALIDITY_YEARS = 30


def _fail(message: str) -> "NoReturn":  # type: ignore[name-defined]
    print(f"::error::{message}", file=sys.stderr)
    sys.exit(1)


def _write(args, *, out_path, p12_bytes, certificate, subject, serial, not_before, not_after) -> None:
    """Writes the PKCS#12 (and optional PEM) and prints a summary."""
    out_path = out_path or os.path.abspath(args.out)
    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    with open(out_path, "wb") as fh:
        fh.write(p12_bytes)
    os.chmod(out_path, 0o600)

    if args.cert_out:
        from cryptography.hazmat.primitives import serialization as _s
        with open(args.cert_out, "wb") as fh:
            fh.write(certificate.public_bytes(_s.Encoding.PEM))

    from cryptography.hazmat.primitives import hashes as _h
    print(f"Keystore:      {out_path}")
    print(f"Bytes:         {len(p12_bytes)}")
    print(f"Alias:         {KEY_ALIAS}")
    print(f"Password:      {STORE_PASSWORD}")
    print(f"Subject:       {subject.rfc4514_string()}")
    print(f"Serial:        {serial}")
    print(f"Not before:    {not_before.isoformat()}")
    print(f"Not after:     {not_after.isoformat()}")
    print(f"Cert SHA-256:  {certificate.fingerprint(_h.SHA256()).hex()}")
    print(f"Cert source:   {'committed PEM (--cert-in)' if args.cert_in else 'generated now'}")
    print(f"Seed source:   {'ASTRA_DEBUG_SEED' if 'ASTRA_DEBUG_SEED' in os.environ else 'built-in default'}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True, help="Path for the generated PKCS#12 file")
    parser.add_argument(
        "--cert-out",
        default=None,
        help="Optional path to also write the PEM-encoded certificate (for verification)",
    )
    parser.add_argument(
        "--cert-in",
        default=None,
        help=(
            "Reuse this PEM certificate instead of generating one. This is what makes the "
            "signing identity byte-stable across runs: ECDSA certificate signatures are "
            "randomized, and Android compares the *certificate bytes* when deciding whether "
            "an update may replace an installed package."
        ),
    )
    args = parser.parse_args()

    try:
        from cryptography import x509
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.asymmetric import ec
        from cryptography.hazmat.primitives.serialization import pkcs12
        from cryptography.x509.oid import NameOID
    except ImportError:
        _fail("The 'cryptography' package is required: pip install cryptography")

    seed = os.environ.get("ASTRA_DEBUG_SEED", DEFAULT_SEED)
    if not seed:
        _fail("ASTRA_DEBUG_SEED is empty; using an empty seed would be a mistake.")

    # --- derive the key -----------------------------------------------------
    # SHA-256 of the seed gives 32 uniformly-distributed bytes. Reduce it into the
    # P-256 scalar range. The modulo introduces negligible bias relative to the
    # order and is irrelevant for a non-secret debug identity.
    digest = hashlib.sha256(seed.encode("utf-8")).digest()
    order = 0xFFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551
    scalar = int.from_bytes(digest, "big") % (order - 1) + 1
    private_key = ec.derive_private_key(scalar, ec.SECP256R1())

    public_numbers = private_key.public_key().public_numbers()
    # Serial number derived from the public key -> stable across runs.
    # X.509 requires a positive serial of at most 159 bits, so mask to 158 bits.
    serial = int.from_bytes(
        hashlib.sha256(public_numbers.x.to_bytes(32, "big") + public_numbers.y.to_bytes(32, "big")).digest(),
        "big",
    ) & ((1 << 158) - 1)
    if serial <= 0:
        _fail("Derived a non-positive certificate serial number.")

    cert_in = args.cert_in
    if cert_in:
        with open(cert_in, "rb") as fh:
            certificate = x509.load_pem_x509_certificate(fh.read())
        # Guard against a seed/certificate mismatch, which would otherwise surface
        # as an opaque apksigner error much later in the pipeline.
        derived = private_key.public_key().public_bytes(
            serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo
        )
        committed = certificate.public_key().public_bytes(
            serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo
        )
        if derived != committed:
            _fail(
                "The derived key does not match the supplied certificate. "
                "ASTRA_DEBUG_SEED was changed without regenerating the certificate, "
                "or --cert-in points at the wrong file."
            )
        subject = certificate.subject
        serial = certificate.serial_number
        not_before = certificate.not_valid_before_utc
        not_after = certificate.not_valid_after_utc
        p12_bytes = pkcs12.serialize_key_and_certificates(
            name=KEY_ALIAS.encode("utf-8"),
            key=private_key,
            cert=certificate,
            cas=None,
            encryption_algorithm=serialization.BestAvailableEncryption(STORE_PASSWORD.encode("utf-8")),
        )
        _write(args, out_path=None, p12_bytes=p12_bytes, certificate=certificate,
               subject=subject, serial=serial, not_before=not_before, not_after=not_after)
        return 0

    # Fixed validity window -> identical certificate fields every run.
    not_before = datetime(2024, 1, 1, tzinfo=timezone.utc)
    not_after = not_before + timedelta(days=365 * VALIDITY_YEARS)

    subject = issuer = x509.Name(
        [
            x509.NameAttribute(NameOID.COMMON_NAME, SUBJECT_CN.removeprefix("CN=")),
            x509.NameAttribute(NameOID.ORGANIZATION_NAME, SUBJECT_O.removeprefix("O=")),
            x509.NameAttribute(NameOID.COUNTRY_NAME, SUBJECT_C.removeprefix("C=")),
        ]
    )

    builder = (
        x509.CertificateBuilder()
        .subject_name(subject)
        .issuer_name(issuer)
        .public_key(private_key.public_key())
        .serial_number(serial)
        .not_valid_before(not_before)
        .not_valid_after(not_after)
        .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
        .add_extension(
            x509.KeyUsage(
                digital_signature=True,
                content_commitment=False,
                key_encipherment=False,
                data_encipherment=False,
                key_agreement=False,
                key_cert_sign=False,
                crl_sign=False,
                encipher_only=False,
                decipher_only=False,
            ),
            critical=True,
        )
        .add_extension(
            x509.SubjectKeyIdentifier.from_public_key(private_key.public_key()),
            critical=False,
        )
    )
    certificate = builder.sign(private_key=private_key, algorithm=hashes.SHA256())

    # --- write the PKCS#12 --------------------------------------------------
    p12_bytes = pkcs12.serialize_key_and_certificates(
        name=KEY_ALIAS.encode("utf-8"),
        key=private_key,
        cert=certificate,
        cas=None,
        encryption_algorithm=serialization.BestAvailableEncryption(STORE_PASSWORD.encode("utf-8")),
    )

    _write(args, out_path=None, p12_bytes=p12_bytes, certificate=certificate,
           subject=subject, serial=serial, not_before=not_before, not_after=not_after)
    return 0


if __name__ == "__main__":
    sys.exit(main())
