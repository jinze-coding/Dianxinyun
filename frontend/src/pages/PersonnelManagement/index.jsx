import React, { useEffect, useMemo, useState } from "react";
import {
  addPersonnel,
  createPersonnelCertificate,
  deletePersonnel,
  deletePersonnelCertificate,
  enterPersonnel,
  exitPersonnel,
  getPersonnelCertificates,
  getPersonnelMovements,
  getPersonnelSummary,
  updatePersonnel,
} from "../../services/personnel";
import { createTraining, markTrainingComplete } from "../../services/safety";
import { uploadFile } from "../../services/file";

const EMPTY_PERSON = {
  name: "",
  gender: "男",
  phone: "",
  idcard: "",
  unit: "",
  role: "",
  remark: "",
};
const EMPTY_TRAINING = {
  batchName: "",
  trainingTime: "",
  trainingPlace: "",
  trainer: "",
  personIds: [],
  remark: "",
};
const EMPTY_CERTIFICATE = {
  personId: "",
  certificateType: "",
  certificateNo: "",
  issueDate: "",
  expiryDate: "",
  remark: "",
  file: null,
};

const statusLabel = (person) =>
  person.statusLabel ||
  { WAIT_EDUCATION: "待教育", EDUCATED: "已教育", LEFT: "已离场" }[
    person.status
  ] ||
  person.status ||
  "-";
const trainingStatusLabel = (training) =>
  training.statusLabel ||
  { NOT_STARTED: "未开始", IN_PROGRESS: "进行中", COMPLETED: "已完成" }[
    training.status
  ] ||
  training.status ||
  "-";
const formatTime = (value) =>
  value ? String(value).replace("T", " ").slice(0, 16) : "-";

function PersonnelManagementPage({ projectId, theme: T }) {
  const [summary, setSummary] = useState(null);
  const [certificates, setCertificates] = useState([]);
  const [movements, setMovements] = useState([]);
  const [activeTab, setActiveTab] = useState("ledger");
  const [keyword, setKeyword] = useState("");
  const [loading, setLoading] = useState(false);
  const [errorText, setErrorText] = useState("");
  const [modal, setModal] = useState(null);
  const [selectedPerson, setSelectedPerson] = useState(null);
  const [personForm, setPersonForm] = useState(EMPTY_PERSON);
  const [trainingForm, setTrainingForm] = useState(EMPTY_TRAINING);
  const [certificateForm, setCertificateForm] = useState(EMPTY_CERTIFICATE);
  const [submitting, setSubmitting] = useState(false);

  const load = async () => {
    if (!projectId) return;
    setLoading(true);
    setErrorText("");
    try {
      const [summaryRes, certRes] = await Promise.all([
        getPersonnelSummary(projectId),
        getPersonnelCertificates(projectId),
      ]);
      if (summaryRes.code !== 200)
        throw new Error(summaryRes.message || "人员数据加载失败");
      if (certRes.code !== 200)
        throw new Error(certRes.message || "证件数据加载失败");
      setSummary(summaryRes.data);
      setCertificates(certRes.data || []);
    } catch (error) {
      setSummary(null);
      setCertificates([]);
      setErrorText(error.message || "人员数据加载失败");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    setKeyword("");
    setModal(null);
    setSelectedPerson(null);
    load();
  }, [projectId]);

  const people = summary?.people || [];
  const trainings = summary?.trainings || [];
  const visiblePeople = useMemo(() => {
    const text = keyword.trim().toLowerCase();
    if (!text) return people;
    return people.filter((person) =>
      `${person.name}${person.team || ""}${person.trade || ""}${person.phone || person.maskedPhone || ""}`
        .toLowerCase()
        .includes(text),
    );
  }, [people, keyword]);

  const canManage = Boolean(summary?.canManage);
  const currentPersonName = (id) =>
    people.find((item) => Number(item.id) === Number(id))?.name || "-";
  const fieldStyle = {
    width: "100%",
    boxSizing: "border-box",
    padding: "8px 10px",
    borderRadius: 6,
    border: `1px solid ${T.borderColor}`,
    background: T.surface2,
    color: T.textPrimary,
    outline: "none",
    fontSize: 12,
  };
  const buttonStyle = (kind = "primary") => ({
    padding: "7px 13px",
    borderRadius: 6,
    border: kind === "secondary" ? `1px solid ${T.borderColor}` : "none",
    background:
      kind === "danger"
        ? T.danger
        : kind === "secondary"
          ? T.surface2
          : T.accent,
    color: kind === "secondary" ? T.textSecondary : "#fff",
    cursor: "pointer",
    fontSize: 12,
    whiteSpace: "nowrap",
  });

  const openPerson = async (person) => {
    setSelectedPerson(person);
    setMovements([]);
    setModal("detail");
    try {
      const res = await getPersonnelMovements(person.id);
      if (res.code === 200) setMovements(res.data || []);
    } catch (_) {
      setMovements([]);
    }
  };

  const submitPerson = async () => {
    if (!personForm.name?.trim()) return alert("请填写人员姓名");
    setSubmitting(true);
    try {
      const payload = { ...personForm, projectId };
      const res =
        modal === "edit"
          ? await updatePersonnel(selectedPerson.id, payload)
          : await addPersonnel(payload);
      if (res.code !== 200) throw new Error(res.message || "保存失败");
      setModal(null);
      await load();
    } catch (error) {
      alert(error.message || "保存失败");
    } finally {
      setSubmitting(false);
    }
  };

  const movePerson = async (action) => {
    if (!selectedPerson) return;
    const label = action === "entry" ? "重新进场" : "办理离场";
    if (!window.confirm(`确认为 ${selectedPerson.name} ${label}？`)) return;
    setSubmitting(true);
    try {
      const res =
        action === "entry"
          ? await enterPersonnel(selectedPerson.id, {})
          : await exitPersonnel(selectedPerson.id, {});
      if (res.code !== 200) throw new Error(res.message || `${label}失败`);
      setModal(null);
      await load();
    } catch (error) {
      alert(error.message || `${label}失败`);
    } finally {
      setSubmitting(false);
    }
  };

  const removePerson = async (person) => {
    if (!window.confirm(`确认删除 ${person.name}？历史流水和培训记录会保留。`))
      return;
    const res = await deletePersonnel(person.id);
    if (res.code !== 200) return alert(res.message || "删除失败");
    setModal(null);
    await load();
  };

  const submitTraining = async () => {
    if (
      !trainingForm.batchName.trim() ||
      !trainingForm.trainingTime ||
      !trainingForm.trainer.trim()
    )
      return alert("请填写培训名称、时间和讲师");
    if (!trainingForm.personIds.length) return alert("请至少选择一名参训人员");
    setSubmitting(true);
    try {
      const res = await createTraining({
        ...trainingForm,
        projectId,
        eduType: "三级安全教育",
        time: `${trainingForm.trainingTime}:00`,
        place: trainingForm.trainingPlace,
      });
      if (res.code !== 200) throw new Error(res.message || "培训创建失败");
      setModal(null);
      setTrainingForm(EMPTY_TRAINING);
      await load();
    } catch (error) {
      alert(error.message || "培训创建失败");
    } finally {
      setSubmitting(false);
    }
  };

  const completeTraining = async (training) => {
    if (!window.confirm(`确认完成「${training.title}」？`)) return;
    const res = await markTrainingComplete(training.id);
    if (res.code !== 200) return alert(res.message || "操作失败");
    await load();
  };

  const submitCertificate = async () => {
    if (
      !certificateForm.personId ||
      !certificateForm.certificateType.trim() ||
      !certificateForm.certificateNo.trim()
    )
      return alert("请填写持证人、证件类型和编号");
    setSubmitting(true);
    try {
      let fileId;
      if (certificateForm.file) {
        const uploadRes = await uploadFile({
          file: certificateForm.file,
          projectId,
          fileType: "人员证件",
          businessType: "PERSON_CERTIFICATE_PENDING",
        });
        if (uploadRes.code !== 200)
          throw new Error(uploadRes.message || "证件附件上传失败");
        fileId = uploadRes.data.id;
      }
      const res = await createPersonnelCertificate(
        Number(certificateForm.personId),
        {
          ...certificateForm,
          file: undefined,
          fileId,
          issueDate: certificateForm.issueDate || null,
          expiryDate: certificateForm.expiryDate || null,
        },
      );
      if (res.code !== 200) throw new Error(res.message || "证件保存失败");
      setModal(null);
      setCertificateForm(EMPTY_CERTIFICATE);
      await load();
    } catch (error) {
      alert(error.message || "证件保存失败");
    } finally {
      setSubmitting(false);
    }
  };

  const openAttachment = async (fileId) => {
    if (!fileId) return;
    const token = localStorage.getItem("site_platform_token");
    const response = await fetch(`/api/files/${fileId}/download`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (!response.ok) return alert("附件打开失败");
    const url = URL.createObjectURL(await response.blob());
    window.open(url, "_blank", "noopener,noreferrer");
    setTimeout(() => URL.revokeObjectURL(url), 60000);
  };

  const exportCsv = () => {
    const rows = [
      ["姓名", "班组", "工种", "手机号", "身份证", "进场时间", "状态"],
      ...visiblePeople.map((item) => [
        item.name,
        item.team,
        item.trade,
        item.phone || item.maskedPhone,
        item.idcard || item.maskedIdcard,
        formatTime(item.entryTime),
        statusLabel(item),
      ]),
    ];
    const blob = new Blob(
      [
        "\ufeff" +
          rows
            .map((row) => row.map((value) => `"${value || ""}"`).join(","))
            .join("\n"),
      ],
      { type: "text/csv;charset=utf-8" },
    );
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `人员台账_${new Date().toISOString().slice(0, 10)}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  };

  const renderStatus = (label, tone = "normal") => (
    <span
      style={{
        display: "inline-flex",
        padding: "3px 8px",
        borderRadius: 999,
        fontSize: 11,
        fontWeight: 700,
        color:
          tone === "danger"
            ? T.danger
            : tone === "warning"
              ? T.warning
              : tone === "success"
                ? T.success
                : T.textSecondary,
        background:
          tone === "danger"
            ? `${T.danger}16`
            : tone === "warning"
              ? `${T.warning}18`
              : tone === "success"
                ? `${T.success}16`
                : T.surface2,
      }}
    >
      {label}
    </span>
  );

  return (
    <div
      style={{
        height: "100%",
        padding: 16,
        display: "flex",
        flexDirection: "column",
        gap: 12,
        overflow: "hidden",
        background: T.pageBg,
      }}
    >
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(4,minmax(0,1fr))",
          background: T.cardBg,
          border: `1px solid ${T.borderColor}`,
          borderRadius: 8,
          overflow: "hidden",
          flexShrink: 0,
        }}
      >
        {[
          ["在场人数", summary?.onsiteCount || 0, T.accent],
          ["今日进场", summary?.todayEntryCount || 0, T.success],
          ["待安全教育", summary?.pendingEducationCount || 0, T.warning],
          ["证件预警", summary?.certificateWarningCount || 0, T.danger],
        ].map(([label, value, color], index) => (
          <div
            key={label}
            style={{
              padding: "14px 18px",
              borderLeft: index ? `1px solid ${T.borderColor}` : "none",
            }}
          >
            <div style={{ fontSize: 11, color: T.textMuted }}>{label}</div>
            <div style={{ marginTop: 5, fontSize: 25, fontWeight: 800, color }}>
              {value}
            </div>
          </div>
        ))}
      </div>

      <div
        style={{
          background: T.cardBg,
          border: `1px solid ${T.borderColor}`,
          borderRadius: 8,
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          gap: 12,
          padding: "9px 12px",
          flexShrink: 0,
        }}
      >
        <div style={{ display: "flex", gap: 6 }}>
          {[
            ["ledger", "人员台账"],
            ["movement", "进退场记录"],
            ["training", "安全教育"],
            ["certificate", "证件管理"],
          ].map(([id, label]) => (
            <button
              key={id}
              onClick={() => setActiveTab(id)}
              style={{
                ...buttonStyle(activeTab === id ? "primary" : "secondary"),
                background: activeTab === id ? T.accent : T.surface2,
              }}
            >
              {label}
            </button>
          ))}
        </div>
        <div style={{ display: "flex", gap: 7, alignItems: "center" }}>
          {activeTab === "ledger" && (
            <>
              <input
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                placeholder="搜索姓名、班组、工种"
                style={{ ...fieldStyle, width: 220 }}
              />
              <button onClick={exportCsv} style={buttonStyle("secondary")}>
                导出
              </button>
              <button
                disabled={!canManage}
                onClick={() => {
                  setPersonForm(EMPTY_PERSON);
                  setModal("add");
                }}
                style={buttonStyle()}
              >
                新增人员
              </button>
            </>
          )}
          {activeTab === "training" && (
            <button
              disabled={!canManage}
              onClick={() => {
                setTrainingForm({
                  ...EMPTY_TRAINING,
                  trainingTime: `${new Date().toISOString().slice(0, 10)}T09:00`,
                });
                setModal("training");
              }}
              style={buttonStyle()}
            >
              发起教育
            </button>
          )}
          {activeTab === "certificate" && (
            <button
              disabled={!canManage}
              onClick={() => {
                setCertificateForm(EMPTY_CERTIFICATE);
                setModal("certificate");
              }}
              style={buttonStyle()}
            >
              新增证件
            </button>
          )}
          {loading && (
            <span style={{ color: T.textMuted, fontSize: 12 }}>加载中...</span>
          )}
        </div>
      </div>

      <div
        style={{
          flex: 1,
          minHeight: 0,
          overflow: "auto",
          background: T.cardBg,
          border: `1px solid ${T.borderColor}`,
          borderRadius: 8,
        }}
      >
        {errorText ? (
          <div style={{ padding: 24, color: T.danger }}>
            {errorText}{" "}
            <button onClick={load} style={buttonStyle("secondary")}>
              重试
            </button>
          </div>
        ) : activeTab === "ledger" ? (
          <>
            <TableHead
              T={T}
              columns="1.1fr .8fr .8fr 1fr 1.1fr .8fr 150px"
              labels={[
                "姓名",
                "班组",
                "工种",
                "手机号",
                "进场时间",
                "状态",
                "操作",
              ]}
            />
            {visiblePeople.map((person) => (
              <TableRow
                key={person.id}
                T={T}
                columns="1.1fr .8fr .8fr 1fr 1.1fr .8fr 150px"
              >
                <strong>{person.name}</strong>
                <span>{person.team || "-"}</span>
                <span>{person.trade || "-"}</span>
                <span>{person.phone || person.maskedPhone || "-"}</span>
                <span>{formatTime(person.entryTime)}</span>
                {renderStatus(
                  statusLabel(person),
                  person.status === "LEFT"
                    ? "normal"
                    : person.status === "WAIT_EDUCATION"
                      ? "warning"
                      : "success",
                )}
                <span style={{ display: "flex", gap: 6 }}>
                  <button
                    onClick={() => openPerson(person)}
                    style={buttonStyle("secondary")}
                  >
                    详情
                  </button>
                  {canManage && (
                    <button
                      onClick={() => {
                        setSelectedPerson(person);
                        setPersonForm({
                          ...EMPTY_PERSON,
                          ...person,
                          unit: person.team,
                          role: person.trade,
                          phone: person.phone || "",
                          idcard: person.idcard || "",
                        });
                        setModal("edit");
                      }}
                      style={buttonStyle("secondary")}
                    >
                      编辑
                    </button>
                  )}
                </span>
              </TableRow>
            ))}
            {!visiblePeople.length && <Empty T={T} text="当前项目暂无人员" />}
          </>
        ) : activeTab === "movement" ? (
          <>
            <TableHead
              T={T}
              columns="1fr .7fr 1.1fr 1fr 1.5fr"
              labels={["人员", "动作", "发生时间", "操作人", "备注"]}
            />
            {people
              .flatMap((person) =>
                person.id === selectedPerson?.id
                  ? movements.map((item) => ({
                      ...item,
                      personName: person.name,
                    }))
                  : [],
              )
              .map((item) => (
                <TableRow
                  key={item.id}
                  T={T}
                  columns="1fr .7fr 1.1fr 1fr 1.5fr"
                >
                  <strong>{item.personName}</strong>
                  {renderStatus(
                    item.actionType === "ENTRY" ? "进场" : "离场",
                    item.actionType === "ENTRY" ? "success" : "normal",
                  )}
                  <span>{formatTime(item.occurredAt)}</span>
                  <span>{item.operatorName || "-"}</span>
                  <span>{item.remark || "-"}</span>
                </TableRow>
              ))}
            {!movements.length && (
              <Empty
                T={T}
                text="进退场流水在人员详情中查看，选择人员后将显示记录"
              />
            )}
          </>
        ) : activeTab === "training" ? (
          <>
            <TableHead
              T={T}
              columns="1.4fr .9fr 1fr .8fr .7fr 140px"
              labels={[
                "培训名称",
                "培训时间",
                "讲师/地点",
                "参训人数",
                "状态",
                "操作",
              ]}
            />
            {trainings.map((training) => (
              <TableRow
                key={training.id}
                T={T}
                columns="1.4fr .9fr 1fr .8fr .7fr 140px"
              >
                <strong>{training.title}</strong>
                <span>{formatTime(training.trainingTime)}</span>
                <span>
                  {training.trainer || "-"} / {training.place || "-"}
                </span>
                <span>{training.personCount || 0} 人</span>
                {renderStatus(
                  trainingStatusLabel(training),
                  training.status === "COMPLETED" ? "success" : "warning",
                )}
                <span>
                  {canManage && training.status !== "COMPLETED" && (
                    <button
                      onClick={() => completeTraining(training)}
                      style={buttonStyle()}
                    >
                      完成培训
                    </button>
                  )}
                </span>
              </TableRow>
            ))}
            {!trainings.length && <Empty T={T} text="暂无安全教育记录" />}
          </>
        ) : (
          <>
            <TableHead
              T={T}
              columns="1fr 1fr 1fr .8fr .8fr 150px"
              labels={[
                "持证人",
                "证件类型",
                "证件编号",
                "到期日期",
                "状态",
                "操作",
              ]}
            />
            {certificates.map((cert) => (
              <TableRow
                key={cert.id}
                T={T}
                columns="1fr 1fr 1fr .8fr .8fr 150px"
              >
                <strong>{currentPersonName(cert.personId)}</strong>
                <span>{cert.certificateType}</span>
                <span>{cert.certificateNo}</span>
                <span>{cert.expiryDate || "-"}</span>
                {renderStatus(
                  cert.warningLabel,
                  cert.warningLevel === "EXPIRED"
                    ? "danger"
                    : cert.warningLevel === "WARNING"
                      ? "warning"
                      : "success",
                )}
                <span style={{ display: "flex", gap: 6 }}>
                  {cert.fileId && (
                    <button
                      onClick={() => openAttachment(cert.fileId)}
                      style={buttonStyle("secondary")}
                    >
                      附件
                    </button>
                  )}
                  {canManage && (
                    <button
                      onClick={async () => {
                        if (window.confirm("确认删除该证件？")) {
                          await deletePersonnelCertificate(cert.id);
                          await load();
                        }
                      }}
                      style={buttonStyle("danger")}
                    >
                      删除
                    </button>
                  )}
                </span>
              </TableRow>
            ))}
            {!certificates.length && <Empty T={T} text="暂无人员证件" />}
          </>
        )}
      </div>

      {modal && (
        <Modal
          T={T}
          title={
            modal === "add"
              ? "新增人员"
              : modal === "edit"
                ? "编辑人员"
                : modal === "detail"
                  ? "人员详情"
                  : modal === "training"
                    ? "发起三级安全教育"
                    : "新增人员证件"
          }
          onClose={() => setModal(null)}
        >
          {(modal === "add" || modal === "edit") && (
            <>
              <FormGrid
                T={T}
                fieldStyle={fieldStyle}
                values={personForm}
                setValues={setPersonForm}
              />
              <ModalActions
                T={T}
                buttonStyle={buttonStyle}
                submitting={submitting}
                onCancel={() => setModal(null)}
                onSubmit={submitPerson}
              />
            </>
          )}
          {modal === "detail" && selectedPerson && (
            <>
              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "repeat(2,1fr)",
                  gap: 10,
                  fontSize: 12,
                  color: T.textSecondary,
                }}
              >
                {[
                  ["姓名", selectedPerson.name],
                  ["状态", statusLabel(selectedPerson)],
                  [
                    "手机号",
                    selectedPerson.phone || selectedPerson.maskedPhone,
                  ],
                  [
                    "身份证",
                    selectedPerson.idcard || selectedPerson.maskedIdcard,
                  ],
                  ["班组", selectedPerson.team],
                  ["工种", selectedPerson.trade],
                ].map(([label, value]) => (
                  <div
                    key={label}
                    style={{
                      padding: 10,
                      background: T.surface2,
                      borderRadius: 6,
                    }}
                  >
                    <span style={{ color: T.textMuted }}>{label}</span>
                    <strong
                      style={{
                        display: "block",
                        marginTop: 4,
                        color: T.textPrimary,
                      }}
                    >
                      {value || "-"}
                    </strong>
                  </div>
                ))}
              </div>
              <div
                style={{
                  marginTop: 14,
                  color: T.textPrimary,
                  fontSize: 13,
                  fontWeight: 800,
                }}
              >
                进退场流水
              </div>
              <div style={{ maxHeight: 180, overflow: "auto", marginTop: 8 }}>
                {movements.map((item) => (
                  <div
                    key={item.id}
                    style={{
                      padding: "8px 0",
                      borderBottom: `1px solid ${T.borderColor}`,
                      fontSize: 12,
                      color: T.textSecondary,
                    }}
                  >
                    {item.actionType === "ENTRY" ? "进场" : "离场"} ·{" "}
                    {formatTime(item.occurredAt)} · {item.operatorName || "-"}
                  </div>
                ))}
                {!movements.length && (
                  <span style={{ color: T.textMuted, fontSize: 12 }}>
                    暂无流水
                  </span>
                )}
              </div>
              {canManage && (
                <div
                  style={{
                    display: "flex",
                    justifyContent: "space-between",
                    marginTop: 16,
                  }}
                >
                  <button
                    onClick={() => removePerson(selectedPerson)}
                    style={buttonStyle("danger")}
                  >
                    删除人员
                  </button>
                  <div style={{ display: "flex", gap: 8 }}>
                    {selectedPerson.status === "LEFT" ? (
                      <button
                        disabled={submitting}
                        onClick={() => movePerson("entry")}
                        style={buttonStyle()}
                      >
                        重新进场
                      </button>
                    ) : (
                      <button
                        disabled={submitting}
                        onClick={() => movePerson("exit")}
                        style={buttonStyle("secondary")}
                      >
                        办理离场
                      </button>
                    )}
                  </div>
                </div>
              )}
            </>
          )}
          {modal === "training" && (
            <>
              <label style={labelStyle(T)}>
                培训名称
                <input
                  style={fieldStyle}
                  value={trainingForm.batchName}
                  onChange={(e) =>
                    setTrainingForm({
                      ...trainingForm,
                      batchName: e.target.value,
                    })
                  }
                />
              </label>
              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "1fr 1fr",
                  gap: 10,
                }}
              >
                <label style={labelStyle(T)}>
                  培训时间
                  <input
                    type="datetime-local"
                    style={fieldStyle}
                    value={trainingForm.trainingTime}
                    onChange={(e) =>
                      setTrainingForm({
                        ...trainingForm,
                        trainingTime: e.target.value,
                      })
                    }
                  />
                </label>
                <label style={labelStyle(T)}>
                  讲师
                  <input
                    style={fieldStyle}
                    value={trainingForm.trainer}
                    onChange={(e) =>
                      setTrainingForm({
                        ...trainingForm,
                        trainer: e.target.value,
                      })
                    }
                  />
                </label>
              </div>
              <label style={labelStyle(T)}>
                培训地点
                <input
                  style={fieldStyle}
                  value={trainingForm.trainingPlace}
                  onChange={(e) =>
                    setTrainingForm({
                      ...trainingForm,
                      trainingPlace: e.target.value,
                    })
                  }
                />
              </label>
              <div
                style={{ marginTop: 10, color: T.textSecondary, fontSize: 12 }}
              >
                选择参训人员
              </div>
              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "repeat(3,1fr)",
                  gap: 8,
                  maxHeight: 180,
                  overflow: "auto",
                  marginTop: 8,
                }}
              >
                {people
                  .filter((person) => person.status !== "LEFT")
                  .map((person) => (
                    <label
                      key={person.id}
                      style={{
                        padding: 8,
                        borderRadius: 6,
                        border: `1px solid ${T.borderColor}`,
                        color: T.textSecondary,
                        fontSize: 12,
                      }}
                    >
                      <input
                        type="checkbox"
                        checked={trainingForm.personIds.includes(person.id)}
                        onChange={(e) =>
                          setTrainingForm({
                            ...trainingForm,
                            personIds: e.target.checked
                              ? [...trainingForm.personIds, person.id]
                              : trainingForm.personIds.filter(
                                  (id) => id !== person.id,
                                ),
                          })
                        }
                      />{" "}
                      {person.name} · {statusLabel(person)}
                    </label>
                  ))}
              </div>
              <ModalActions
                T={T}
                buttonStyle={buttonStyle}
                submitting={submitting}
                onCancel={() => setModal(null)}
                onSubmit={submitTraining}
              />
            </>
          )}
          {modal === "certificate" && (
            <>
              <label style={labelStyle(T)}>
                持证人
                <select
                  style={fieldStyle}
                  value={certificateForm.personId}
                  onChange={(e) =>
                    setCertificateForm({
                      ...certificateForm,
                      personId: e.target.value,
                    })
                  }
                >
                  <option value="">请选择</option>
                  {people.map((person) => (
                    <option key={person.id} value={person.id}>
                      {person.name}
                    </option>
                  ))}
                </select>
              </label>
              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "1fr 1fr",
                  gap: 10,
                }}
              >
                <label style={labelStyle(T)}>
                  证件类型
                  <input
                    style={fieldStyle}
                    value={certificateForm.certificateType}
                    onChange={(e) =>
                      setCertificateForm({
                        ...certificateForm,
                        certificateType: e.target.value,
                      })
                    }
                    placeholder="如：低压电工证"
                  />
                </label>
                <label style={labelStyle(T)}>
                  证件编号
                  <input
                    style={fieldStyle}
                    value={certificateForm.certificateNo}
                    onChange={(e) =>
                      setCertificateForm({
                        ...certificateForm,
                        certificateNo: e.target.value,
                      })
                    }
                  />
                </label>
                <label style={labelStyle(T)}>
                  发证日期
                  <input
                    type="date"
                    style={fieldStyle}
                    value={certificateForm.issueDate}
                    onChange={(e) =>
                      setCertificateForm({
                        ...certificateForm,
                        issueDate: e.target.value,
                      })
                    }
                  />
                </label>
                <label style={labelStyle(T)}>
                  到期日期
                  <input
                    type="date"
                    style={fieldStyle}
                    value={certificateForm.expiryDate}
                    onChange={(e) =>
                      setCertificateForm({
                        ...certificateForm,
                        expiryDate: e.target.value,
                      })
                    }
                  />
                </label>
              </div>
              <label style={labelStyle(T)}>
                证件附件
                <input
                  type="file"
                  style={fieldStyle}
                  onChange={(e) =>
                    setCertificateForm({
                      ...certificateForm,
                      file: e.target.files?.[0] || null,
                    })
                  }
                />
              </label>
              <ModalActions
                T={T}
                buttonStyle={buttonStyle}
                submitting={submitting}
                onCancel={() => setModal(null)}
                onSubmit={submitCertificate}
              />
            </>
          )}
        </Modal>
      )}
    </div>
  );
}

function TableHead({ T, columns, labels }) {
  return (
    <div
      style={{
        display: "grid",
        gridTemplateColumns: columns,
        gap: 10,
        padding: "10px 14px",
        position: "sticky",
        top: 0,
        zIndex: 1,
        background: T.surface2,
        borderBottom: `1px solid ${T.borderColor}`,
        color: T.textMuted,
        fontSize: 11,
      }}
    >
      {labels.map((label) => (
        <span key={label}>{label}</span>
      ))}
    </div>
  );
}
function TableRow({ T, columns, children }) {
  return (
    <div
      style={{
        display: "grid",
        gridTemplateColumns: columns,
        gap: 10,
        alignItems: "center",
        minHeight: 48,
        padding: "8px 14px",
        borderBottom: `1px solid ${T.borderColor}`,
        color: T.textSecondary,
        fontSize: 12,
      }}
    >
      {children}
    </div>
  );
}
function Empty({ T, text }) {
  return (
    <div
      style={{
        padding: 36,
        textAlign: "center",
        color: T.textMuted,
        fontSize: 12,
      }}
    >
      {text}
    </div>
  );
}
function Modal({ T, title, onClose, children }) {
  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        zIndex: 1200,
        background: "rgba(10,20,35,.58)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: 24,
      }}
      onClick={onClose}
    >
      <div
        style={{
          width: 680,
          maxWidth: "96vw",
          maxHeight: "88vh",
          overflow: "auto",
          background: T.modalBg,
          border: `1px solid ${T.borderColor}`,
          borderRadius: 8,
          padding: 18,
          boxShadow: "0 18px 50px rgba(0,0,0,.28)",
        }}
        onClick={(event) => event.stopPropagation()}
      >
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            marginBottom: 16,
          }}
        >
          <strong style={{ color: T.textPrimary, fontSize: 16 }}>
            {title}
          </strong>
          <button
            onClick={onClose}
            style={{
              border: 0,
              background: "transparent",
              color: T.textMuted,
              fontSize: 20,
              cursor: "pointer",
            }}
          >
            ×
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}
function ModalActions({ buttonStyle, submitting, onCancel, onSubmit }) {
  return (
    <div
      style={{
        display: "flex",
        justifyContent: "flex-end",
        gap: 8,
        marginTop: 18,
      }}
    >
      <button onClick={onCancel} style={buttonStyle("secondary")}>
        取消
      </button>
      <button disabled={submitting} onClick={onSubmit} style={buttonStyle()}>
        {submitting ? "提交中..." : "确认保存"}
      </button>
    </div>
  );
}
function labelStyle(T) {
  return {
    display: "flex",
    flexDirection: "column",
    gap: 5,
    marginBottom: 10,
    color: T.textSecondary,
    fontSize: 12,
  };
}
function FormGrid({ T, fieldStyle, values, setValues }) {
  return (
    <div
      style={{ display: "grid", gridTemplateColumns: "repeat(2,1fr)", gap: 10 }}
    >
      <label style={labelStyle(T)}>
        姓名 *
        <input
          style={fieldStyle}
          value={values.name || ""}
          onChange={(e) => setValues({ ...values, name: e.target.value })}
        />
      </label>
      <label style={labelStyle(T)}>
        性别
        <select
          style={fieldStyle}
          value={values.gender || "男"}
          onChange={(e) => setValues({ ...values, gender: e.target.value })}
        >
          <option>男</option>
          <option>女</option>
        </select>
      </label>
      <label style={labelStyle(T)}>
        手机号
        <input
          style={fieldStyle}
          value={values.phone || ""}
          onChange={(e) => setValues({ ...values, phone: e.target.value })}
        />
      </label>
      <label style={labelStyle(T)}>
        身份证
        <input
          style={fieldStyle}
          value={values.idcard || ""}
          onChange={(e) => setValues({ ...values, idcard: e.target.value })}
        />
      </label>
      <label style={labelStyle(T)}>
        班组
        <input
          style={fieldStyle}
          value={values.unit || ""}
          onChange={(e) => setValues({ ...values, unit: e.target.value })}
        />
      </label>
      <label style={labelStyle(T)}>
        工种
        <input
          style={fieldStyle}
          value={values.role || ""}
          onChange={(e) => setValues({ ...values, role: e.target.value })}
        />
      </label>
      <label style={{ ...labelStyle(T), gridColumn: "1/-1" }}>
        备注
        <textarea
          style={{ ...fieldStyle, minHeight: 70 }}
          value={values.remark || ""}
          onChange={(e) => setValues({ ...values, remark: e.target.value })}
        />
      </label>
    </div>
  );
}

export default PersonnelManagementPage;
