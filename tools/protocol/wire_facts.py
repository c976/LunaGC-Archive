"""Syntax checks shared by reference production and loading, independent of proof status."""


def validate_directional_wire_facts(number, key, value):
    """Reject impossible protobuf wire facts without promoting valid facts to proof."""
    if (type(number) is not int or not 0 < number < 2**29
            or not isinstance(value, list) or any(type(n) is not int for n in value)):
        raise ValueError('invalid directional wire facts')
    if key == 'accepted_wire_types':
        if any(not 0 <= n <= 5 for n in value):
            raise ValueError('invalid directional wire facts: accepted wire types must be in 0..5')
    elif key == 'serialized_tags':
        if any(n <= 0 or n >> 3 != number or (n & 7) > 5 for n in value):
            raise ValueError('invalid directional wire facts: Writer tag contradicts its field number or wire type')
    else:
        raise ValueError('unsupported directional wire property')
