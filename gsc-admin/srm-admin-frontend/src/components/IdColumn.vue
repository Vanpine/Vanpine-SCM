<template>
  <el-table-column
    type="index"
    :index="rowIndex"
    label="ID"
    :width="width"
    align="center"
  />
</template>

<script setup>
const props = defineProps({
  /** 当前页码，有分页时传入，默认第 1 页 */
  pageNum: { type: Number, default: 1 },
  /** 每页条数，有分页时传入；不传则本页从 1 起编 */
  pageSize: { type: Number, default: 0 },
  /** 列宽，表头 ID 需要留出空间 */
  width: { type: [Number, String], default: 80 },
})

/** 第 2 页、每页 10 条：idx=0 → 11 */
function rowIndex(idx) {
  const page = Number(props.pageNum) || 1
  const size = Number(props.pageSize) || 0
  return (page - 1) * size + idx + 1
}
</script>
