const { get, put } = require('../../utils/request')

const ROLE_LABEL = {
  REPORTER: '师生',
  REPAIRER: '维修工',
  ADMIN: '后勤管理员',
  SYS_ADMIN: '系统管理员',
}

Page({
  data: {
    user: {},
    roleLabel: '',
    isReporter: false,
    isRepairer: false,
    phone: '',
    department: '',
    address: '',
    campusList: [],
    campusIndex: 0,
    campusCode: '',
    campusName: '',
    campusAddress: '',
    workerCampuses: '',
    workerSkills: '',
    saving: false,
    loaded: false,
    loadError: false,
  },

  onLoad() {
    this.load()
  },

  async load() {
    this.setData({ loadError: false })
    try {
      const [me, campuses] = await Promise.all([get('/auth/me'), get('/campuses')])
      const isReporter = me.role === 'REPORTER'
      const isRepairer = me.role === 'REPAIRER'
      // 首位放占位项：未填写校区时用户选第一项也能触发 change
      const options = [{ code: '', name: '请选择校区' }].concat(campuses || [])
      let idx = me.campus ? options.findIndex(c => c.code === me.campus) : 0
      if (idx < 0) idx = 0
      const campus = options[idx] && options[idx].code ? options[idx] : null

      this.setData({
        user: me,
        roleLabel: ROLE_LABEL[me.role] || me.role,
        isReporter,
        isRepairer,
        phone: me.phone || '',
        department: me.department || '',
        address: me.address || '',
        campusList: options,
        campusIndex: idx,
        campusCode: me.campus || '',
        campusName: me.campusName || '',
        campusAddress: campus ? (campus.address || '') : '',
        loaded: true,
      })

      if (isRepairer) {
        try {
          const w = await get('/workers/me')
          this.setData({
            workerCampuses: (w.campuses || []).join('、') || '由后勤分配',
            workerSkills: (w.skills || []).join('、') || '未配置',
          })
        } catch (e) { /* 档案加载失败不影响改手机号 */ }
      }
    } catch (e) {
      this.setData({ loadError: true })
    }
  },

  onCampusChange(e) {
    const i = Number(e.detail.value)
    const c = this.data.campusList[i]
    if (!c || !c.code) {
      this.setData({ campusIndex: i, campusCode: '', campusName: '', campusAddress: '' })
      return
    }
    this.setData({
      campusIndex: i,
      campusCode: c.code,
      campusName: c.name,
      campusAddress: c.address || '',
    })
  },

  onPhone(e) { this.setData({ phone: e.detail.value }) },
  onDepartment(e) { this.setData({ department: e.detail.value }) },
  onAddress(e) { this.setData({ address: e.detail.value }) },

  async onSave() {
    const { isReporter, isRepairer, phone, department, address, campusCode } = this.data
    const phoneTrim = phone.trim()

    if (!/^1[3-9]\d{9}$/.test(phoneTrim)) {
      wx.showToast({ title: '请输入正确的手机号', icon: 'none' })
      return
    }
    if (isReporter && !campusCode) {
      wx.showToast({ title: '请选择所属校区', icon: 'none' })
      return
    }

    const body = { phone: phoneTrim }
    if (isReporter) {
      body.campus = campusCode
      body.department = department.trim()
      body.address = address.trim()
    } else if (!isRepairer) {
      // 管理员只维护部门与电话；维修工服务校区/技能由后勤维护
      body.department = department.trim()
    }

    this.setData({ saving: true })
    try {
      await put('/auth/me', body)
      wx.showToast({ title: '资料已保存', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 500)
    } catch (e) { /* 拦截器已提示 */ }
    finally { this.setData({ saving: false }) }
  },
})
