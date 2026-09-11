export const environment = {
  production: true,
  api: {
    directory: {
      url: 'https://natiart.samuelpetre.com/server/directory',
      endpoints: {
        login: '/login',
        logout: '/signout',
        refreshToken: '/refresh-token',
        registerUser: '/register-user',
        current: '/current',
        user: '/users',
      }
    },
    product: {
      url: 'https://natiart.samuelpetre.com/server/product',
      endpoints: {
        category: '/categories',
        package: '/packages',
        product: '/products',
        order: '/orders',
      }
    },
    viaCep: {
      url: 'https://viacep.com.br/ws',
    },
  }
};
