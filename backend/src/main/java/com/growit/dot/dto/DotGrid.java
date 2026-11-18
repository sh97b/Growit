package com.growit.dot.dto;

import java.util.List;

/** cells는 채워지는 순서(아래 줄부터, 같은 줄은 왼쪽부터)로 놓인다. 진행 칸 수 K는 앞의 K칸이 칠해졌다는 뜻이다. */
public record DotGrid(int width, int height, List<String> palette, List<DotCell> cells) {
}
