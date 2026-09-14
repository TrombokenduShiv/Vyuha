"""Atomic artifact replacement, including transient Windows/OneDrive file locks."""
import os
from pathlib import Path
import time
import uuid
import torch


def save_checkpoint(payload, destination):
    destination = Path(destination)
    temporary = destination.with_name(destination.name + "." + uuid.uuid4().hex + ".tmp")
    try:
        torch.save(payload, temporary)
        for attempt in range(10):
            try:
                os.replace(temporary, destination)
                break
            except PermissionError:
                if attempt == 9:
                    raise
                time.sleep(.2)
    finally:
        temporary.unlink(missing_ok=True)


def write_bytes_atomic(destination, content):
    destination = Path(destination)
    temporary = destination.with_name(destination.name + "." + uuid.uuid4().hex + ".tmp")
    try:
        temporary.write_bytes(content)
        for attempt in range(10):
            try:
                os.replace(temporary, destination)
                break
            except PermissionError:
                if attempt == 9:
                    raise
                time.sleep(.2)
    finally:
        temporary.unlink(missing_ok=True)
