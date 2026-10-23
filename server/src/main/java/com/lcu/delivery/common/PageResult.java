package com.lcu.delivery.common;

import java.util.List;

public record PageResult<T>(List<T> list, long total, long page, long size) {
}
