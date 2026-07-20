export interface SpotCheckCategory {
  label: string;
  value: string;
  template: string;
  sampleProblem: string;
}

export const spotCheckCategories: SpotCheckCategory[] = [
  {
    label: '箱体外观',
    value: 'APPEARANCE',
    template: '恢复箱门闭合，清理箱体周边杂物，整改后上传外观照片。',
    sampleProblem: '箱门未关闭，箱体周边存在杂物。'
  },
  {
    label: '漏电保护器',
    value: 'LEAKAGE_PROTECTOR',
    template: '检查漏电保护器动作状态，异常部件需更换，整改后上传内部照片。',
    sampleProblem: '漏电保护器状态异常或未按要求配置。'
  },
  {
    label: '熔断/开关',
    value: 'FUSE',
    template: '核查熔断器和开关配置，恢复规范接线并上传整改照片。',
    sampleProblem: '熔断器或开关配置不规范。'
  },
  {
    label: '保护接零',
    value: 'PROTECTIVE_ZERO',
    template: '补齐保护接零措施，确认连接牢固，整改后上传接线照片。',
    sampleProblem: '保护接零缺失或连接不牢固。'
  },
  {
    label: '插座/用电',
    value: 'SOCKET',
    template: '整理插座和临时用电线路，消除私拉乱接，整改后上传照片。',
    sampleProblem: '插座或临时用电线路存在私拉乱接。'
  },
  {
    label: '环境/通道',
    value: 'ENVIRONMENT',
    template: '清理电箱周边环境，保持通道畅通，整改后上传现场照片。',
    sampleProblem: '电箱周边通道不畅或堆放材料。'
  },
  {
    label: '其他',
    value: 'OTHER',
    template: '按现场安全要求完成整改，并上传整改前后对比照片。',
    sampleProblem: '现场存在其他不符合安全管理要求的问题。'
  }
];

export function formatSpotCheckCategory(value?: string) {
  return spotCheckCategories.find((item) => item.value === value)?.label || value || '未分类';
}
