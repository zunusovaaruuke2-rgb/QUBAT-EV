"""Expose the failing test and full stack in CI without dumping the entire report."""
from pathlib import Path
import xml.etree.ElementTree as ET

for report in Path('app/build/outputs/androidTest-results').rglob('*.xml'):
    try:
        root = ET.parse(report).getroot()
    except ET.ParseError:
        continue
    for case in root.iter('testcase'):
        for failure in list(case.findall('failure')) + list(case.findall('error')):
            print(case.get('classname'), case.get('name'))
            print(failure.text or failure.get('message'))
