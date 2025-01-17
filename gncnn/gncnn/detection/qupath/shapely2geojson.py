"""
Original:
    Copyright (c) 2018 Alex Vykaliuk
    License: MIT
Modified by: Israel Mateos-Aparicio-Ruiz
Modifications:
    - Adapted output to match the QuPath format

As this source code is licensed under the MIT license, a copy of the license
terms is provided in the LICENSE_SHAPELY-GEOJSON file in the root of this
repository, as stated in the license terms. However, the main code is licensed 
under the GPL-3.0 license, as stated in the LICENSE file in the root of this
repository.
"""
import json
import uuid

from shapely.geometry import mapping, Polygon
from shapely.geometry.base import BaseGeometry


class Feature():
    def __init__(self, geometry, properties=None):
        self.geometry = geometry
        self.properties = properties

    @property
    def geometry(self):
        return self._geometry

    @geometry.setter
    def geometry(self, geometry):
        if not isinstance(geometry, BaseGeometry):
            raise ValueError('geometry must be a shapely geometry.')
        self._geometry = geometry

    @property
    def properties(self):
        return self._properties

    @properties.setter
    def properties(self, properties):
        if properties is None:
            self._properties = {}
        elif isinstance(properties, dict):
            self._properties = properties
        else:
            raise ValueError('properties must be a dict.')

    @property
    def __geo_interface__(self):
        return {
            'type': 'Feature',
            'id': str(uuid.uuid4()),
            'geometry': self.geometry.__geo_interface__,
            'properties': self.properties,
        }

    def __eq__(self, other):
        return self.__geo_interface__ == other.__geo_interface__


class FeatureCollection():
    def __init__(self, objects):
        self.features = objects

    @property
    def features(self):
        return self._features

    @features.setter
    def features(self, objects):
        all_are_features = all(
            isinstance(feature, Feature)
            for feature in objects
        )
        if all_are_features:
            self._features = objects
        else:
            try:
                self._features = [
                    Feature(geometry)
                    for geometry in objects
                ]
            except ValueError:
                raise ValueError(
                    'features can be either a Feature or shapely geometry.')

    def __iter__(self):
        return iter(self.features)

    def geometries_iterator(self):
        for feature in self.features:
            yield feature.geometry

    @property
    def __geo_interface__(self):
        return {
            'type': 'FeatureCollection',
            'features': [
                feature.__geo_interface__
                for feature in self.features
            ],
        }

    def __eq__(self, other):
        return self.__geo_interface__ == other.__geo_interface__


def dump(obj, fp, *args, **kwargs):
    """Dump shapely geometry object :obj: to a file :fp:."""
    json.dump(mapping(obj), fp, *args, **kwargs)


def dumps(obj, *args, **kwargs):
    """Dump shapely geometry object :obj: to a string."""
    return json.dumps(mapping(obj), *args, **kwargs)


def poly2geojson(polygons, class_name, class_color, path_to_geojson):
    features = []
    for polygon in polygons:
        features.append(Feature(Polygon(polygon), {
            "objectType": "annotation",
            "classification": {
                "name": class_name,
                "color": class_color
            }
        }))

    with open(path_to_geojson, "w") as fp:
        feature_dict = json.loads(dumps(FeatureCollection(features)))
        json.dump(feature_dict, fp)
    
    return feature_dict