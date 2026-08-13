class ShipmentFieldMapper {
  const ShipmentFieldMapper._();

  static String firstNonEmpty(List<dynamic> values) {
    for (final value in values) {
      if (value == null) continue;
      final text = value.toString().trim();
      if (text.isEmpty || text.toLowerCase() == 'null') continue;
      return text;
    }
    return '';
  }

  /// Recursively searches for the first occurring value from [keys].
  /// [excludePaths] allows skipping branches (e.g., skip "customer" for store names).
  static dynamic _findRawValue(dynamic node, List<String> keys, {List<String>? excludePaths}) {
    final wanted = keys.map((k) => k.toLowerCase().replaceAll(RegExp(r'[^a-z0-9]'), '')).toSet();
    final excluded = excludePaths?.map((k) => k.toLowerCase().replaceAll(RegExp(r'[^a-z0-9]'), '')).toSet() ?? {};

    dynamic walk(dynamic value, String currentPath) {
      if (value is Map) {
        // First check immediate children for a match to respect priority within this branch
        for (final entry in value.entries) {
          final key = entry.key.toString().toLowerCase().replaceAll(RegExp(r'[^a-z0-9]'), '');
          if (excluded.contains(key)) continue;
          if (wanted.contains(key) && entry.value != null) {
            final val = entry.value.toString().trim();
            if (val.isNotEmpty && val.toLowerCase() != 'null') return entry.value;
          }
        }
        // If no immediate match, recurse
        for (final entry in value.entries) {
          final key = entry.key.toString().toLowerCase().replaceAll(RegExp(r'[^a-z0-9]'), '');
          if (excluded.contains(key)) continue;
          final found = walk(entry.value, key);
          if (found != null) return found;
        }
      } else if (value is List) {
        for (final item in value) {
          final found = walk(item, currentPath);
          if (found != null) return found;
        }
      }
      return null;
    }

    return walk(node, '');
  }

  static String recipientName(Map<String, dynamic> json) {
    return firstNonEmpty([
      json['delivery_location_name'],
      json['delivery_location_contact'],
      json['consignee_name'],
      json['recipient_name'],
      _findRawValue(json, ['delivery_location_name', 'delivery_location_contact', 'consignee_name', 'recipient_name']),
    ]);
  }

  static String recipientPhone(Map<String, dynamic> json) {
    return firstNonEmpty([
      json['delivery_phone'],
      json['consignee_phone'],
      json['recipient_phone'],
      _findRawValue(json, ['delivery_phone', 'consignee_phone', 'recipient_phone', 'mobile', 'phone']),
    ]);
  }

  static String merchantName(Map<String, dynamic> json) {
    // Priority: collection_location_contact -> collection_location_name -> requested_by
    // Exclude: customer branches
    return firstNonEmpty([
      json['collection_location_contact'],
      json['collection_location_name'],
      json['requested_by'],
      _findRawValue(json, ['collection_location_contact', 'collection_location_name', 'requested_by', 'store_name', 'merchant_name'], excludePaths: ['customer']),
    ]);
  }

  static String shipmentNumber(Map<String, dynamic> json) {
    return firstNonEmpty([
      json['order_id'],
      json['outgoing_tn'],
      json['reference_no'],
      _findRawValue(json, ['order_id', 'outgoing_tn', 'reference_no', 'order_awb', 'awb']),
    ]);
  }

  static String shipmentStatus(Map<String, dynamic> json) {
    return firstNonEmpty([
      json['status_label'],
      json['status'],
      _findRawValue(json, ['status_label', 'status', 'order_status_label']),
    ]);
  }

  static String recipientAddress(Map<String, dynamic> json) {
    final parts = <String>[];
    
    // Attempt to find these specific keys anywhere in the JSON
    final addr1 = _findRawValue(json, ['delivery_location_address1', 'address1', 'address']);
    final addr2 = _findRawValue(json, ['delivery_location_address2', 'address2']);
    final area = _findRawValue(json, ['delivery_area_name', 'area_name', 'district']);
    final city = _findRawValue(json, ['delivery_location_city', 'city']);
    final zip = _findRawValue(json, ['delivery_postal_code', 'postal_code', 'zip_code']);

    final candidates = [addr1, addr2, area, city, zip];

    for (final candidate in candidates) {
      if (candidate == null) continue;
      final text = candidate.toString().trim();
      if (text.isEmpty || text.toLowerCase() == 'null') continue;

      final normalizedText = _normalize(text);
      final isDuplicate = parts.any((existing) {
        final normalizedExisting = _normalize(existing);
        return normalizedExisting == normalizedText || normalizedExisting.contains(normalizedText);
      });

      if (!isDuplicate) {
        parts.add(text);
      }
    }

    return parts.join('، ');
  }

  static double codAmount(Map<String, dynamic> json) {
    final value = _findRawValue(json, ['cod_amount', 'amount_to_collect', 'collect_amount', 'collectable_amount']);
    if (value == null) return 0;
    if (value is num) return value.toDouble();
    return double.tryParse(value.toString().trim().replaceAll(',', '')) ?? 0;
  }

  static bool isCod(Map<String, dynamic> json) {
    final value = _findRawValue(json, ['is_cod']);
    final enabled = value == true ||
        value == 1 ||
        value?.toString().trim().toLowerCase() == '1' ||
        value?.toString().trim().toLowerCase() == 'true';

    return enabled || codAmount(json) > 0;
  }

  static String formatAmount(double value) {
    if (!value.isFinite) return '0';
    if (value == value.truncateToDouble()) return value.toInt().toString();
    var result = value.toStringAsFixed(2);
    result = result.replaceFirst(RegExp(r'0+$'), '');
    result = result.replaceFirst(RegExp(r'\.$'), '');
    return result;
  }

  static String _normalize(String value) {
    return value
        .trim()
        .toLowerCase()
        .replaceAll(RegExp(r'\s+'), ' ')
        .replaceAll('،', ',');
  }
}
