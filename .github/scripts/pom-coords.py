"""Prints "groupId artifactId version" for a pom, resolving what it inherits from its parent."""

import sys
import xml.etree.ElementTree as ET

NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def inherited(root, tag):
    # An Element with no children is falsy, so these must be compared against None explicitly.
    found = root.find("m:" + tag, NS)
    if found is None:
        found = root.find("m:parent/m:" + tag, NS)
    if found is None:
        raise SystemExit("no {} in {} or its parent block".format(tag, sys.argv[1]))
    return found.text


def main():
    root = ET.parse(sys.argv[1]).getroot()
    print(inherited(root, "groupId"), root.find("m:artifactId", NS).text, inherited(root, "version"))


if __name__ == "__main__":
    main()
