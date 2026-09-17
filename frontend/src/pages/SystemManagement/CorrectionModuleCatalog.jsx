const modules = {
  '场内管理': { icon: 'visitors', description: '维护来访信息、会议登记与资料内容。' },
  '资料与用印': { icon: 'folder', description: '核对工程资料、收发说明和用印申请。' },
  '电箱巡检': { icon: 'electric', description: '更正电箱台账、日检及整改记录。' },
  '临边巡检': { icon: 'barrier', description: '维护点位信息、检查及整改内容。' },
  '质量周检': { icon: 'clipboard', description: '核对周检内容、已有问题和质量资料。' },
  '安委会巡检': { icon: 'shield', description: '更正巡检分类、检查信息及附件。' },
  '项目信息': { icon: 'building', description: '维护项目档案、图片和地理位置。' },
};

const paths = {
  visitors: ['M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2', 'M16 3a4 4 0 0 1 0 8', 'M22 21v-2a4 4 0 0 0-3-3.87'],
  folder: ['M3 7V5a2 2 0 0 1 2-2h4l2 3h8a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7Z', 'M3 10h18', 'M8 15h8'],
  electric: ['M5 3h14v18H5z', 'm13 6-4 7h6l-4 5'],
  barrier: ['M3 7h18v7H3z', 'm6 7 5 7m2-7 5 7', 'M6 14v7m12-7v7'],
  clipboard: ['M9 5H6a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-3', 'M9 2h6v5H9z', 'm8 14 3 3 5-6'],
  shield: ['m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6l8-3Z', 'm8 12 3 3 5-6'],
  building: ['M4 21V3h12v18M2 21h20M16 10h4v11', 'M8 7h4m-4 4h4m-4 4h4m-2 3v3'],
  edit: ['M12 4H5a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2h13a2 2 0 0 0 2-2v-7', 'm16 3 5 5-10 10-6 1 1-6L16 3Z', 'm13 6 5 5'],
};

function CatalogIcon({ name }) {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {(paths[name] || paths.folder).map((d) => <path key={d} d={d} />)}
    {name === 'visitors' && <circle cx="9" cy="7" r="4" />}
  </svg>;
}

export function CorrectionPageHeader() {
  return <header className="correction-page-header">
    <span className="correction-page-icon"><CatalogIcon name="edit" /></span>
    <div className="correction-page-heading">
      <div className="correction-page-title"><h2>数据纠错</h2><span className="correction-admin-badge"><CatalogIcon name="shield" />管理员专属</span></div>
      <p>选择业务记录，核对修改内容。每次纠错均保留原因与完整操作记录。</p>
    </div>
  </header>;
}

export default function CorrectionModuleCatalog({ catalog, onSelect }) {
  const groups = [...new Set(catalog.map((type) => type.group))];
  return <div className="correction-catalog-workspace">
    <div className="correction-catalog-overview">
      <div><h3>选择业务模块</h3><p>从需要纠正的数据类型开始</p></div>
      <div className="correction-catalog-count"><strong>{groups.length}</strong> 个业务模块<span /><strong>{catalog.length}</strong> 类记录</div>
    </div>
    <ol className="correction-catalog-steps" aria-label="数据纠错操作步骤">
      {['选择业务模块', '筛选并修改记录', '核对前后内容并保存'].map((step, index) => <li key={step} className={index === 0 ? 'is-current' : ''}><span>{index + 1}</span>{step}</li>)}
    </ol>
    {!catalog.length && <p className="correction-catalog-loading" role="status">正在加载业务模块…</p>}
    <div className="correction-catalog" aria-label="业务模块目录">
      {groups.map((group) => {
        const info = modules[group] || { icon: 'folder', description: '选择记录类型，查询并更正业务内容。' };
        const entries = catalog.filter((type) => type.group === group);
        return <section className="correction-module-card" key={group} aria-label={group}>
          <div className="correction-module-heading">
            <span className="correction-module-icon"><CatalogIcon name={info.icon} /></span>
            <h3>{group}</h3><span className="correction-module-count">{entries.length} 类记录</span>
          </div>
          <p className="correction-module-description">{info.description}</p>
          <div className="correction-module-entries">
            {entries.map((type) => <button className="correction-module-entry" type="button" key={type.code} onClick={() => onSelect(type)}>
              <span className="correction-entry-dot" aria-hidden="true" /><span className="correction-entry-label">{type.label}</span>
              <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true"><path d="m7 5 5 5-5 5" strokeLinecap="round" strokeLinejoin="round" /></svg>
            </button>)}
          </div>
        </section>;
      })}
    </div>
    <p className="correction-catalog-note"><CatalogIcon name="clipboard" />纠错历史可在记录详情中查看，也可从系统操作日志进入。</p>
  </div>;
}
